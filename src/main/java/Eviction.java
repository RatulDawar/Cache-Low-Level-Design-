import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

public class Eviction<KEY, VALUE> {
    private EvictionStrategy evictionStrategy;
    private DataSource<KEY,VALUE> dataSource;
    private final Map<KEY,Record<VALUE>> keyValueStore;
    private final ConcurrentSkipListMap<Long,List<Record<VALUE>>> expiryQueue;
    private final ConcurrentSkipListMap<AccessDetails,List<Record<VALUE>>> priorityQueue;
    private OrderingFactory<AccessDetails> orderingFactory;
    private final Integer EXPIRY_TIME_IN_MILIS;
    private final Integer THRESHOLD_ENTRIES;
    private final Integer POOL_SIZE = 5;
    private final ExecutorService threadPool[];
    public Eviction(EvictionStrategy evictionStrategy, Integer EXPIRY_TIME_IN_MILIS, Integer THRESHOLD_ENTRIES){
        this.evictionStrategy = evictionStrategy;
        orderingFactory = new OrderingFactory<AccessDetails>(evictionStrategy);

        Comparator<AccessDetails> customComparator = orderingFactory.getCache();
        this.expiryQueue = new ConcurrentSkipListMap<>();
        this.priorityQueue = new ConcurrentSkipListMap<>(customComparator);
        this.keyValueStore = new ConcurrentHashMap<>();
        this.EXPIRY_TIME_IN_MILIS = EXPIRY_TIME_IN_MILIS;
        this.THRESHOLD_ENTRIES = THRESHOLD_ENTRIES;
        this.threadPool = new ExecutorService[POOL_SIZE];
        for(int i = 0;i<POOL_SIZE;i++){
            this.threadPool[i] = Executors.newSingleThreadExecutor();
        }
    }
    public Eviction(Integer EXPIRY_TIME_IN_MILIS, Integer THRESHOLD_ENTRIES){
        this.evictionStrategy = EvictionStrategy.LRU;
        orderingFactory = new OrderingFactory<AccessDetails>(evictionStrategy);
        Comparator<AccessDetails> customComparator = orderingFactory.getCache();
        this.expiryQueue = new ConcurrentSkipListMap<>();
        this.priorityQueue = new ConcurrentSkipListMap<>(customComparator);
        this.keyValueStore = new ConcurrentHashMap<>();
        this.EXPIRY_TIME_IN_MILIS = EXPIRY_TIME_IN_MILIS;
        this.THRESHOLD_ENTRIES = THRESHOLD_ENTRIES;
        this.threadPool = new ExecutorService[POOL_SIZE];
        for(int i = 0;i<POOL_SIZE;i++){
            this.threadPool[i] = Executors.newSingleThreadExecutor();
        }
    }

    public CompletableFuture<VALUE> get(KEY key){
        return CompletableFuture.supplyAsync(() -> getInAssignedThread(key),threadPool[key.hashCode()%POOL_SIZE]).thenCompose(Function.identity());
    }
    public CompletableFuture<Void> set(KEY key, VALUE value){
        return CompletableFuture.supplyAsync(()->setInAssignedThread(key,value),threadPool[key.hashCode()%POOL_SIZE]).thenCompose(Function.identity());
    }
    private CompletableFuture<VALUE> getInAssignedThread(KEY key){
        CompletableFuture<Record<VALUE>> requiredValue = null;
        if(keyValueStore.containsKey(key)){
            Record<VALUE> record = keyValueStore.get(key);
            if(!isExpired(key)) {
                requiredValue = CompletableFuture.completedFuture((keyValueStore.get(key)));
            }else{
                expiryQueue.get(record.loadTime).remove(record);
                priorityQueue.get(record.accessDetails).remove(record);
            }

        }
        if(requiredValue == null) {
            requiredValue = dataSource.get(key)
                    .thenCompose(value -> this.addToCache(key, value)
                    .thenApply(__ -> new Record<VALUE>(value)));
        }

        return requiredValue.thenApply(valueRecord -> {
            CompletableFuture<Record<VALUE>> temp = this.updateInCache(key);
            Record<VALUE> result = temp.handle((value,execption) -> {
                return value;
            }).join();
            return result.getValue();

        });

    }

    public CompletableFuture<Void> setInAssignedThread(KEY key, VALUE value){
        Record<VALUE> valueRecord = new Record<>(value);
        Long oldestLoadTime = expiryQueue.firstKey();

        // remove expired keys
        while(!expiryQueue.isEmpty() && isExpired(oldestLoadTime)){
            expiryQueue.remove(oldestLoadTime);
            if(!expiryQueue.isEmpty())oldestLoadTime = expiryQueue.firstKey();
        }

        if(keyValueStore.containsKey(key)){
            keyValueStore.put(key,valueRecord);
            this.updateInCache(key);

        }else{
            if(keyValueStore.size() >= this.THRESHOLD_ENTRIES){
                AccessDetails lowestPriorityAccess = priorityQueue.firstKey();
                priorityQueue.remove(lowestPriorityAccess);
            }
            this.addToCache(key,value);
            this.updateInCache(key);

        }
        return new CompletableFuture<>();
    }
    private CompletableFuture<Record<VALUE>> addToCache(KEY key,VALUE value){
        Record<VALUE> record =new Record<>(value);
        Long currentTime = System.currentTimeMillis();
        record.loadTime = currentTime;
        keyValueStore.put(key,record);
        expiryQueue.putIfAbsent(record.accessDetails.lastAccessTimeInMilis,new CopyOnWriteArrayList<>());
        expiryQueue.get(record.accessDetails.lastAccessTimeInMilis).add(record);
        return CompletableFuture.completedFuture(keyValueStore.get(key));
    }
    private CompletableFuture<Record<VALUE>> updateInCache(KEY key){
        Record<VALUE> valueRecord = keyValueStore.get(key);
        valueRecord.accessDetails.accessFrequency += 1;
        valueRecord.accessDetails.lastAccessTimeInMilis = System.currentTimeMillis();
        priorityQueue.putIfAbsent(valueRecord.accessDetails,new CopyOnWriteArrayList<>());
        priorityQueue.get(valueRecord.accessDetails).add(valueRecord);
        keyValueStore.put(key,valueRecord);
        return CompletableFuture.completedFuture(keyValueStore.get(key));
    }

    public Boolean isExpired(KEY key){
        return keyValueStore.get(key).loadTime + this.EXPIRY_TIME_IN_MILIS < System.currentTimeMillis();
    }
    public Boolean isExpired(Long loadTime){
        return loadTime + this.EXPIRY_TIME_IN_MILIS < System.currentTimeMillis();
    }


}


class Record<VALUE>{
    private VALUE value;
    AccessDetails accessDetails;
    Long loadTime;
    public Record(VALUE value){
        this.value = value;
    }
    public VALUE getValue(){
        return value;
    }
    public void setValue(VALUE value){
        this.value = value;
    }

}
class AccessDetails{
    Long lastAccessTimeInMilis;
    Long accessFrequency;
}