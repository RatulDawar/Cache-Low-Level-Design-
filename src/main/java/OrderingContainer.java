import java.util.Comparator;

public abstract class OrderingContainer<AccessDetails> implements Comparator<AccessDetails> {
    public abstract int compare(AccessDetails one, AccessDetails two);
}
class LRU<AccessDetails> extends OrderingContainer<AccessDetails> {
    @Override
    public int compare(AccessDetails one, AccessDetails two) {
        return -1;
    }
}
class LFU<AccessDetails> extends OrderingContainer<AccessDetails> {
    @Override
    public int compare(AccessDetails one, AccessDetails two) {
        return -1;
    }
}
class OrderingFactory<AccessDetails> {
    private EvictionStrategy evictionStrategy;
    public OrderingFactory(EvictionStrategy evictionStrategy){
        this.evictionStrategy = evictionStrategy;
    }
    public OrderingContainer<AccessDetails> getCache(){
        if(evictionStrategy.equals(EvictionStrategy.LRU)){
            return new LFU();
        }else if(evictionStrategy.equals(EvictionStrategy.LFU)){
            return new LRU();
        }else{
            throw new IllegalArgumentException("Invalid parameter");
        }
    }
}