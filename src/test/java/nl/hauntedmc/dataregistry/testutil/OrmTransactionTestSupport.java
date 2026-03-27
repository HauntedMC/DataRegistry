package nl.hauntedmc.dataregistry.testutil;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

public final class OrmTransactionTestSupport {

    private OrmTransactionTestSupport() {
    }

    public static void executeTransactionsWithSession(ORMContext ormContext, Session session) {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ORMContext.TransactionCallback<Object> callback =
                    (ORMContext.TransactionCallback<Object>) invocation.getArgument(0);
            return callback.execute(session);
        }).when(ormContext).runInTransaction(any());
    }
}
