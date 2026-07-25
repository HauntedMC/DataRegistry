#!/usr/bin/env bash
set -euo pipefail

ROOT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT_DIRECTORY
readonly ACCEPTANCE_DIRECTORY="$ROOT_DIRECTORY/dataregistry-platform-acceptance"
readonly COMPOSE_FILE="$ACCEPTANCE_DIRECTORY/docker-compose.yml"
readonly USER_AGENT="DataRegistry-release-validation/1.0 (https://github.com/HauntedMC/DataRegistry)"

if [[ -n "${PLATFORM_ACCEPTANCE_WORK_DIRECTORY:-}" ]]; then
    WORK_DIRECTORY="$PLATFORM_ACCEPTANCE_WORK_DIRECTORY"
    CREATED_WORK_DIRECTORY=false
    mkdir -p "$WORK_DIRECTORY"
else
    WORK_DIRECTORY="$(mktemp -d)"
    CREATED_WORK_DIRECTORY=true
fi
readonly WORK_DIRECTORY
readonly CREATED_WORK_DIRECTORY
readonly KEEP_WORK_DIRECTORY="${PLATFORM_ACCEPTANCE_KEEP_WORK_DIRECTORY:-false}"
readonly JAVA_EXECUTABLE="${PLATFORM_ACCEPTANCE_JAVA:-java}"

paper_process=""
paper_input_fd=""
velocity_process=""
velocity_input_fd=""

diagnostic() {
    printf '%s %s\n' "$(date --iso-8601=seconds)" "$*" >>"$WORK_DIRECTORY/runner.log"
}

cleanup() {
    local exit_code=$?
    diagnostic "Cleanup started with exit code ${exit_code}."
    if [[ -n "$paper_process" ]] && kill -0 "$paper_process" 2>/dev/null; then
        kill "$paper_process" 2>/dev/null || true
    fi
    if [[ -n "$velocity_process" ]] && kill -0 "$velocity_process" 2>/dev/null; then
        kill "$velocity_process" 2>/dev/null || true
    fi
    docker compose --file "$COMPOSE_FILE" logs --no-color >"$WORK_DIRECTORY/backend.log" 2>&1 || true
    docker compose --file "$COMPOSE_FILE" down --volumes --timeout 10 >/dev/null 2>&1 || true
    if [[ $exit_code -ne 0 ]]; then
        find "$WORK_DIRECTORY" -maxdepth 2 -name '*.log' -type f -print -exec tail -n 250 {} \; >&2 || true
    fi
    if [[ "$KEEP_WORK_DIRECTORY" == "true" || "$CREATED_WORK_DIRECTORY" != "true" ]]; then
        echo "Platform acceptance logs retained in $WORK_DIRECTORY" >&2
    else
        rm -rf "$WORK_DIRECTORY"
    fi
    exit "$exit_code"
}
trap cleanup EXIT

fail() {
    echo "Platform acceptance failure: $*" >&2
    exit 1
}

require_java_25_or_newer() {
    local version
    version="$("$JAVA_EXECUTABLE" -version 2>&1 | awk -F '[\".]' '/version/ { print $2; exit }')"
    [[ "$version" =~ ^[0-9]+$ ]] && (( 10#$version >= 25 )) || fail "Platform acceptance requires Java 25 or newer; ${JAVA_EXECUTABLE} reports Java ${version:-unknown}. Set PLATFORM_ACCEPTANCE_JAVA to a Java 25+ executable."
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "Platform acceptance requires '${1}' on PATH."
}

enable_data_provider_multi_release_support() {
    local bundle=$1 manifest="$WORK_DIRECTORY/data-provider-multi-release.mf"
    # DataProvider bundles Byte Buddy's JDK-specific classes. Maven Shade preserves
    # those classes but omits this manifest attribute, so plugin class loading on
    # JDK 24+ cannot see them. Only the disposable acceptance copy is amended.
    printf 'Multi-Release: true\n\n' >"$manifest"
    jar --update --file "$bundle" --manifest "$manifest" >/dev/null 2>&1 \
        || fail "Could not enable multi-release support in $(basename "$bundle")."
}

verify_prerequisites() {
    local command_name
    for command_name in curl docker jq jar sha256sum; do
        require_command "$command_name"
    done
    docker info >/dev/null 2>&1 || fail "Platform acceptance requires a reachable Docker daemon."
    require_java_25_or_newer
}

pom_property() {
    local property_name=$1
    local value
    value="$(awk -v opening_tag="<${property_name}>" -v closing_tag="</${property_name}>" '
        index($0, opening_tag) {
            value = substr($0, index($0, opening_tag) + length(opening_tag))
            closing_tag_index = index(value, closing_tag)
            if (closing_tag_index > 0) print substr(value, 1, closing_tag_index - 1)
            exit
        }
    ' "$ROOT_DIRECTORY/pom.xml")"
    [[ -n "$value" ]] || fail "Missing Maven property ${property_name}."
    printf '%s' "$value"
}

download_runtime() {
    local project=$1 version=$2 build=$3 expected_checksum=$4 destination=$5
    local metadata url actual_checksum
    metadata="$(curl --fail --silent --show-error --location --retry 3 --retry-all-errors \
        --connect-timeout 15 --max-time 120 --header "User-Agent: $USER_AGENT" \
        "https://fill.papermc.io/v3/projects/${project}/versions/${version}/builds")"
    url="$(jq --raw-output --argjson build "$build" \
        '.[] | select(.id == $build) | .downloads["server:default"].url' <<<"$metadata")"
    [[ "$url" != "null" ]] || fail "No ${project} ${version} build ${build} server download exists."
    curl --fail --silent --show-error --location --retry 3 --retry-all-errors --connect-timeout 15 --max-time 120 \
        --output "$destination" "$url"
    actual_checksum="$(sha256sum "$destination" | awk '{print $1}')"
    [[ "$actual_checksum" == "$expected_checksum" ]] || fail "Checksum mismatch for ${project} ${version} build ${build}."
}

backend_port() {
    local endpoint
    endpoint="$(docker compose --file "$COMPOSE_FILE" port mysql 3306 | head -n 1)"
    [[ "$endpoint" =~ :([0-9]+)$ ]] || fail "Could not determine the host MySQL port."
    printf '%s' "${BASH_REMATCH[1]}"
}

wait_for_log() {
    local log_file=$1 expected=$2 timeout_seconds=$3
    local deadline=$((SECONDS + timeout_seconds))
    while (( SECONDS < deadline )); do
        grep -Eq -- "$expected" "$log_file" && return
        if grep -Eq 'DATAREGISTRY_ACCEPTANCE_FAIL|DataRegistry startup failed|Exception in thread|Could not load' "$log_file"; then
            fail "Platform reported a boot or acceptance failure while waiting for ${expected}."
        fi
        sleep 1
    done
    fail "Timed out waiting for ${expected}."
}

stop_process() {
    local process_id=$1 input_fd=$2 stop_command=$3 log_file=$4 disabled_message=$5
    local deadline exit_status
    eval "printf '%s\\n' '$stop_command' >&${input_fd}"
    deadline=$((SECONDS + 45))
    while kill -0 "$process_id" 2>/dev/null && (( SECONDS < deadline )); do sleep 1; done
    kill -0 "$process_id" 2>/dev/null && fail "Platform did not terminate after ${stop_command}."
    set +e
    wait "$process_id"
    exit_status=$?
    set -e
    grep -Eq "$disabled_message" "$log_file" || fail "DataRegistry did not report clean platform shutdown."
    grep -Eq 'HikariPool-.*housekeeper.*still|thread.*did not stop' "$log_file" \
        && fail "Platform reported a leaked DataProvider or DataRegistry worker thread."
    if (( exit_status != 0 )); then
        echo "Platform returned ${exit_status} after a verified clean ${stop_command} shutdown." >&2
    fi
}

write_dataprovider_configuration() {
    local data_directory=$1 owner_plugin=$2 mysql_port=$3
    mkdir -p "$data_directory/databases"
    cat >"$data_directory/config.yml" <<'EOF'
orm:
  schema_mode: validate
databases:
  mysql:
    enabled: true
  mongodb:
    enabled: false
  redis:
    enabled: false
  redis_messaging:
    enabled: false
EOF
    cat >"$data_directory/databases/mysql.yml" <<EOF
player_data_rw:
  access:
    owner_plugin: "${owner_plugin}"
    shared_with: []
  host: 127.0.0.1
  port: ${mysql_port}
  database: minecraft
  username: root
  password: acceptance-root
  ssl_mode: DISABLED
  pool_size: 3
  min_idle: 0
  connection_timeout_ms: 5000
  validation_timeout_ms: 2000
  connect_timeout_ms: 5000
  socket_timeout_ms: 5000
EOF
}

write_dataregistry_configuration() {
    local data_directory=$1
    mkdir -p "$data_directory"
    cp "$ROOT_DIRECTORY/dataregistry-core/src/main/resources/config.yml" "$data_directory/config.yml"
}

start_paper() {
    diagnostic "Starting Paper acceptance."
    local directory="$WORK_DIRECTORY/paper"
    mkdir -p "$directory/plugins/DataProvider" "$directory/plugins/DataRegistry"
    cp "$DATAPROVIDER_PAPER_BUNDLE" "$directory/plugins/DataProvider.jar"
    enable_data_provider_multi_release_support "$directory/plugins/DataProvider.jar"
    cp "$PAPER_BUNDLE" "$directory/plugins/DataRegistry.jar"
    cp "$PAPER_CONSUMER" "$directory/plugins/DataRegistryAcceptance.jar"
    write_dataprovider_configuration "$directory/plugins/DataProvider" "DataRegistry" "$MYSQL_PORT"
    write_dataregistry_configuration "$directory/plugins/DataRegistry"
    printf 'eula=true\n' >"$directory/eula.txt"
    printf 'server-port=0\n' >"$directory/server.properties"
    mkfifo "$directory/console.in"
    (cd "$directory" && exec "$JAVA_EXECUTABLE" -Xms512M -Xmx1G -jar "$WORK_DIRECTORY/paper.jar" --nogui <console.in >paper.log 2>&1) &
    paper_process=$!
    exec {paper_input_fd}>"$directory/console.in"
    wait_for_log "$directory/paper.log" 'DATAREGISTRY_ACCEPTANCE_PASS platform=paper' 180
    stop_process "$paper_process" "$paper_input_fd" stop "$directory/paper.log" 'DataRegistry disabled on Paper'
    eval "exec ${paper_input_fd}>&-"
    paper_input_fd=""
    paper_process=""
    diagnostic "Paper acceptance completed."
}

start_velocity() {
    diagnostic "Starting Velocity acceptance."
    local directory="$WORK_DIRECTORY/velocity"
    mkdir -p "$directory/plugins/dataprovider" "$directory/plugins/dataregistry"
    cp "$DATAPROVIDER_VELOCITY_BUNDLE" "$directory/plugins/DataProvider.jar"
    enable_data_provider_multi_release_support "$directory/plugins/DataProvider.jar"
    cp "$VELOCITY_BUNDLE" "$directory/plugins/DataRegistry.jar"
    cp "$VELOCITY_CONSUMER" "$directory/plugins/DataRegistryAcceptance.jar"
    write_dataprovider_configuration "$directory/plugins/dataprovider" "dataregistry" "$MYSQL_PORT"
    write_dataregistry_configuration "$directory/plugins/dataregistry"
    mkfifo "$directory/console.in"
    (cd "$directory" && exec "$JAVA_EXECUTABLE" -Xms256M -Xmx768M -jar "$WORK_DIRECTORY/velocity.jar" <console.in >velocity.log 2>&1) &
    velocity_process=$!
    exec {velocity_input_fd}>"$directory/console.in"
    wait_for_log "$directory/velocity.log" 'DATAREGISTRY_ACCEPTANCE_PASS platform=velocity' 120
    stop_process "$velocity_process" "$velocity_input_fd" end "$directory/velocity.log" 'DataRegistry disabled on Velocity'
    eval "exec ${velocity_input_fd}>&-"
    velocity_input_fd=""
    velocity_process=""
    diagnostic "Velocity acceptance completed."
}

RELEASE_VERSION="$(pom_property revision)"
readonly RELEASE_VERSION
readonly PAPER_BUNDLE="$ROOT_DIRECTORY/dataregistry-platform-paper/target/dataregistry-platform-paper-${RELEASE_VERSION}-bundled.jar"
readonly VELOCITY_BUNDLE="$ROOT_DIRECTORY/dataregistry-platform-velocity/target/dataregistry-platform-velocity-${RELEASE_VERSION}-bundled.jar"
readonly PAPER_CONSUMER="$ACCEPTANCE_DIRECTORY/consumer-paper/target/dataregistry-acceptance-consumer-paper-${RELEASE_VERSION}.jar"
readonly VELOCITY_CONSUMER="$ACCEPTANCE_DIRECTORY/consumer-velocity/target/dataregistry-acceptance-consumer-velocity-${RELEASE_VERSION}.jar"
readonly MAVEN_REPOSITORY="${MAVEN_REPO_LOCAL:-${HOME}/.m2/repository}"
DATAPROVIDER_VERSION="$(pom_property dataprovider.version)"
readonly DATAPROVIDER_VERSION
readonly DATAPROVIDER_PAPER_BUNDLE="$MAVEN_REPOSITORY/nl/hauntedmc/dataprovider/dataprovider-platform-paper/${DATAPROVIDER_VERSION}/dataprovider-platform-paper-${DATAPROVIDER_VERSION}-bundled.jar"
readonly DATAPROVIDER_VELOCITY_BUNDLE="$MAVEN_REPOSITORY/nl/hauntedmc/dataprovider/dataprovider-platform-velocity/${DATAPROVIDER_VERSION}/dataprovider-platform-velocity-${DATAPROVIDER_VERSION}-bundled.jar"

for artifact in "$PAPER_BUNDLE" "$VELOCITY_BUNDLE" "$PAPER_CONSUMER" "$VELOCITY_CONSUMER" \
        "$DATAPROVIDER_PAPER_BUNDLE" "$DATAPROVIDER_VELOCITY_BUNDLE"; do
    [[ -f "$artifact" ]] || fail "Missing required acceptance artifact ${artifact}."
done
verify_prerequisites
for consumer in "$PAPER_CONSUMER" "$VELOCITY_CONSUMER"; do
    jar tf "$consumer" | grep -Eq '^nl/hauntedmc/dataregistry/api/' \
        && fail "Consumer bundled DataRegistry API classes instead of compiling against the provided API."
done

download_runtime paper "$(pom_property paper.runtime.version)" "$(pom_property paper.runtime.build)" \
    "$(pom_property paper.runtime.sha256)" "$WORK_DIRECTORY/paper.jar"
download_runtime velocity "$(pom_property velocity.version)" "$(pom_property velocity.runtime.build)" \
    "$(pom_property velocity.runtime.sha256)" "$WORK_DIRECTORY/velocity.jar"
docker compose --file "$COMPOSE_FILE" up --detach --wait
MYSQL_PORT="$(backend_port)"
readonly MYSQL_PORT
docker compose --file "$COMPOSE_FILE" exec --no-TTY mysql mysql -uroot -pacceptance-root minecraft -e \
    "INSERT INTO player_entity (uuid, username) VALUES ('8a1c5035-c774-405e-ae4a-0948f0595d12', 'AcceptancePlayer');"
start_paper
start_velocity
diagnostic "All platform acceptance checks passed."
echo "Platform acceptance passed for DataRegistry ${RELEASE_VERSION}."
