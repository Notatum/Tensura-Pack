import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.architectury.registry.registries.forge.RegistrarManagerImpl;
import local.tensura.registrycompat.mixin.RaceRegistrarMixin;
import net.minecraft.core.Registry;
import net.minecraft.resources.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class RegistrarConcurrencyTest {
    static final String[] NS={"tensura","mysticism","trnightmare","ascension","trorigins","trstarlight"};
    static final int[] N={68,186,125,69,32,14};
    static final ResourceKey<Registry<Object>> KEY=ResourceKey.createRegistryKey(ResourceLocation.parse("manascore_race:races"));
    static final Method WRAP;
    static {try{WRAP=RaceRegistrarMixin.class.getDeclaredMethod("tensura$serializeRaceRegistration",ResourceLocation.class,java.util.function.Supplier.class,Operation.class);WRAP.setAccessible(true);}catch(Exception e){throw new RuntimeException(e);}}
    @SuppressWarnings("unchecked")
    static Registry<Object> dummy(ResourceKey<Registry<Object>> key){
        return (Registry<Object>)Proxy.newProxyInstance(Registry.class.getClassLoader(),new Class<?>[]{Registry.class},(p,m,a)->{
            if(m.getName().equals("key"))return key;throw new UnsupportedOperationException(m.getName());
        });
    }
    static RaceRegistrarMixin wrapper(Object registry,Object delegate)throws Exception{
        RaceRegistrarMixin result=new RaceRegistrarMixin();
        for(var e:Map.of("registry",registry,"delegate",delegate).entrySet()){
            var f=RaceRegistrarMixin.class.getDeclaredField(e.getKey());f.setAccessible(true);f.set(result,e.getValue());
        }return result;
    }
    @SuppressWarnings("unchecked")
    static RegistrySupplier<Object> call(RaceRegistrarMixin w,ResourceLocation id,java.util.function.Supplier<Object> factory,Operation<RegistrySupplier<Object>> op)throws Throwable{
        try{return (RegistrySupplier<Object>)WRAP.invoke(w,id,factory,op);}catch(InvocationTargetException e){throw e.getCause();}
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Throwable{
        Set<ResourceLocation> expected=new HashSet<>();for(int t=0;t<NS.length;t++)for(int i=0;i<N[t];i++)expected.add(ResourceLocation.fromNamespaceAndPath(NS[t],"race_"+i));
        for(int round=0;round<300;round++){
            Map<ResourceKey<? extends Registry<?>>,RegistrarManagerImpl.Data<?>> map=new HashMap<>();Registry<Object> delegate=dummy(KEY);
            var a=new RegistrarManagerImpl.RegistrarImpl<Object>("manascore_race",map,delegate);
            var b=new RegistrarManagerImpl.RegistrarImpl<Object>("manascore_race",map,delegate);
            var wa=wrapper(map,delegate);var wb=wrapper(map,delegate);AtomicInteger factoryCalls=new AtomicInteger();
            CyclicBarrier barrier=new CyclicBarrier(NS.length);List<Thread> threads=new ArrayList<>();List<Throwable> failures=Collections.synchronizedList(new ArrayList<>());
            for(int t=0;t<NS.length;t++){
                int index=t;threads.add(Thread.ofPlatform().unstarted(()->{try{
                    barrier.await();for(int i=0;i<N[index];i++){
                        ResourceLocation id=ResourceLocation.fromNamespaceAndPath(NS[index],"race_"+i);
                        var registrar=index%2==0?a:b;var w=index%2==0?wa:wb;
                        RegistrySupplier<Object> returned=call(w,id,()->{factoryCalls.incrementAndGet();return new Object();},values->{
                            if(!Thread.holdsLock(map))throw new AssertionError("Missing provider lock");
                            return registrar.register((ResourceLocation)values[0],(java.util.function.Supplier<Object>)values[1]);
                        });
                        if(!returned.getId().equals(id))throw new AssertionError("Provider changed");
                    }
                }catch(Throwable e){failures.add(e);}}));
            }
            threads.forEach(Thread::start);for(var t:threads)t.join();
            if(!failures.isEmpty())throw new AssertionError(failures.toString());
            var f=RegistrarManagerImpl.Data.class.getDeclaredField("objects");f.setAccessible(true);Map<?,?> pending=(Map<?,?>)f.get(map.get(KEY));
            Set<Object> traversed=new HashSet<>();for(var e:pending.entrySet())traversed.add(e.getKey());
            if(!traversed.equals(expected)||pending.size()!=expected.size()||factoryCalls.get()!=0)throw new AssertionError("Lost entry/eager factory at round"+round);
        }
        Map<Object,Object> lock=new HashMap<>();var other=wrapper(lock,dummy(ResourceKey.createRegistryKey(ResourceLocation.parse("minecraft:item"))));
        call(other,ResourceLocation.parse("test:other"),Object::new,v->{if(Thread.holdsLock(lock))throw new AssertionError("Unrelated registry locked");return null;});
        var race=wrapper(lock,dummy(KEY));boolean thrown=false;
        try{call(race,ResourceLocation.parse("test:throw"),Object::new,v->{throw new IllegalStateException("expected");});}catch(IllegalStateException expectedFailure){thrown=true;}
        if(!thrown||Thread.holdsLock(lock))throw new AssertionError("Exception swallowed or lock retained");
        Thread next=Thread.ofPlatform().start(()->{synchronized(lock){}});next.join(1000);if(next.isAlive())throw new AssertionError("Lock not released to next thread");
        System.out.println("PASS 300 concurrent rounds;494 exact queued IDs,494 unchanged providers,shared lock across two registrar aliases,deferred factories;unrelated registry bypass;exception propagation and monitor release");
    }
}
