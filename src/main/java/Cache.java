import java.util.concurrent.CompletableFuture;

public class Cache<KEY,VALUE> {

    private final PersistanceStrategy persistanceStrategy;
    private final EvictionStrategy evictionStrategy;
    private final Integer EXPIRY_TIME_IN_MILIS;
    private final Integer THRESHOLD_ENTRIES;
    private final Eviction eviction;
    private final DataSource dataSource;

    public Cache(PersistanceStrategy persistanceStrategy, EvictionStrategy evictionStrategy, Integer EXPIRY_TIME_IN_MILIS, Integer THRESHOLD_ENTRIES){
        this.persistanceStrategy = persistanceStrategy;
        this.evictionStrategy = evictionStrategy;
        this.THRESHOLD_ENTRIES = THRESHOLD_ENTRIES;
        this.EXPIRY_TIME_IN_MILIS = EXPIRY_TIME_IN_MILIS;
        this.eviction = new Eviction(evictionStrategy,EXPIRY_TIME_IN_MILIS,THRESHOLD_ENTRIES);
        this.dataSource = new DataSource();
    }
    public CompletableFuture<VALUE> get(KEY key){
        return eviction.get(key);
    }

    public CompletableFuture<Void> set(KEY key,VALUE value){
        if(persistanceStrategy.equals(PersistanceStrategy.WRITE_THROUGH)){
            return dataSource.persist(key,value).thenAccept(__ -> eviction.set(key,value));
        }else{
            eviction.set(key,value);
            dataSource.persist(key,value);
            return CompletableFuture.completedFuture(null);
        }
    }
}

