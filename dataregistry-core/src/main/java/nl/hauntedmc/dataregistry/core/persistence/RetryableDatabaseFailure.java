package nl.hauntedmc.dataregistry.core.persistence;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PessimisticLockException;
import org.hibernate.JDBCException;
import org.hibernate.exception.LockAcquisitionException;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTransientException;

/**
 * Classifies database failures for transaction-level retry. Callers must retry the complete transaction and ensure
 * the operation is idempotent or naturally convergent after reading the newly committed state.
 */
public final class RetryableDatabaseFailure {

    private RetryableDatabaseFailure() {
    }

    public static boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLTransientException
                    || current instanceof LockAcquisitionException
                    || current instanceof PessimisticLockException
                    || current instanceof OptimisticLockException) {
                return true;
            }
            if (current instanceof SQLException sqlException && isRetryableSqlFailure(sqlException)) {
                return true;
            }
            if (current instanceof JDBCException jdbcException
                    && (jdbcException.getSQLException() instanceof SQLTransientException
                    || isRetryableSqlFailure(jdbcException.getSQLException()))) {
                return true;
            }
            if (current instanceof PersistenceException persistenceException
                    && persistenceException.getCause() instanceof SQLException sqlException
                    && isRetryableSqlFailure(sqlException)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isRetryableSqlFailure(SQLException sqlException) {
        if (sqlException == null) {
            return false;
        }
        if (sqlException instanceof SQLIntegrityConstraintViolationException) {
            return true;
        }
        String sqlState = sqlException.getSQLState();
        // Class 23 covers convergent unique-key races; 08 is connection failure; 40 is rollback/deadlock.
        return sqlState != null
                && (sqlState.startsWith("23") || sqlState.startsWith("08") || sqlState.startsWith("40"));
    }
}
