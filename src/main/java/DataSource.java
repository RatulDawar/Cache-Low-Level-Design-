import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class DataSource<KEY,VALUE> {

    public final CompletableFuture<VALUE> get(KEY key){
        return new CompletableFuture<VALUE>();
    }
    public CompletableFuture<Void> persist(KEY key, VALUE value){
        return new CompletableFuture<Void>();
    }
}
