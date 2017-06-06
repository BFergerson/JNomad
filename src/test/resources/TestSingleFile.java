import javax.persistence.EntityManager;
import javax.persistence.Query;

/**
create table table_b (
    a varchar(255),
    column_c varchar(255)
);
*/
public class TestSingleFile {

    private EntityManager manager;

    private void methodWithQuery() {
        Query query = manager.createQuery("select a from table_b where column_c = :val");
        query.setParameter("val", "test");
        query.getResultList();
    }

}