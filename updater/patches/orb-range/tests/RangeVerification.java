import java.lang.reflect.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public final class RangeVerification {
    private static int checks,failures;private static final List<String> failed=new ArrayList<>();
    private static void check(boolean value,String name){checks++;if(!value){failures++;failed.add(name);}}
    private static Object call(Class<?> type,String name,Class<?>[] params,Object...args)throws Exception{Method method=type.getDeclaredMethod(name,params);method.setAccessible(true);return method.invoke(null,args);}
    private static double radius(Class<?> type,int stage)throws Exception{return (Double)call(type,"radius",new Class[]{int.class},stage);}
    private static boolean within(Class<?> type,double square,int stage)throws Exception{return (Boolean)call(type,"withinRadius",new Class[]{double.class,int.class},square,stage);}
    public static void main(String[] args)throws Exception{
        try(var oldLoader=new URLClassLoader(new URL[]{Path.of(args[0]).toUri().toURL()},ClassLoader.getPlatformClassLoader());var newLoader=new URLClassLoader(new URL[]{Path.of(args[1]).toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            Class<?> old=oldLoader.loadClass("ar.juan.devouringorb.OrbBalance"),current=newLoader.loadClass("ar.juan.devouringorb.OrbBalance");
            Class<?> oldMotion=oldLoader.loadClass("ar.juan.devouringorb.AuraMotion"),newMotion=newLoader.loadClass("ar.juan.devouringorb.AuraMotion");
            double[] expected={15,18,22,25,29,32,36,39,43,80};
            check(current.getField("MAX_RADIUS").getDouble(null)==80,"Maximum radius is80");
            for(int stage=0;stage<10;stage++){
                double before=radius(old,stage),r=radius(current,stage),square=r*r;
                check(r==expected[stage]&&r==before+(stage==9?30:0),"stage"+stage+" changes only final radius");
                check(within(current,square,stage),"stage"+stage+" includes exact boundary");
                check(!within(current,Math.nextUp(square),stage),"stage"+stage+" rejects just outside boundary");
                check(within(current,(before+1)*(before+1),stage)==(stage==9)&&!within(old,(before+1)*(before+1),stage),"stage"+stage+" expanded area exists only in final form");
                for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}){
                    double[] position={0,0,0};position[axis]=sign*r;
                    check(within(current,position[0]*position[0]+position[1]*position[1]+position[2]*position[2],stage),"stage"+stage+" includes axis"+axis+" sign"+sign);
                }
                double diagonal=(r-0.001)/Math.sqrt(2);
                check(within(current,diagonal*diagonal*2,stage),"stage"+stage+" includes interior diagonal");
                check(!within(current,square*2,stage),"stage"+stage+" excludes cube corners");
                check(!within(current,-1,stage)&&!within(current,Double.NaN,stage)&&!within(current,Double.POSITIVE_INFINITY,stage),"stage"+stage+" invalid distances rejected");
                for(String method:new String[]{"threshold","cooldown"})check(call(old,method,new Class[]{int.class},stage).equals(call(current,method,new Class[]{int.class},stage)),"stage"+stage+" preserves"+method);
                for(String value:new String[]{"0","0.000001","1000000","10000000","1E400","-1"}){
                    var points=new BigDecimal(value);
                    check(((BigDecimal)call(old,"exactDamage",new Class[]{BigDecimal.class,int.class},points,stage)).compareTo((BigDecimal)call(current,"exactDamage",new Class[]{BigDecimal.class,int.class},points,stage))==0,"stage"+stage+" preserves damage"+value);
                }
                for(int tick:new int[]{0,1,5,9,10,20}){
                    double beforeWave=(Double)call(oldMotion,"waveRadius",new Class[]{int.class,int.class},tick,stage),afterWave=(Double)call(newMotion,"waveRadius",new Class[]{int.class,int.class},tick,stage);
                    check(Math.abs(beforeWave/before-afterWave/r)<1e-12,"stage"+stage+" preserves wave easing"+tick);
                    if(tick>=10)check(afterWave==r,"stage"+stage+" visual wave reaches new radius");
                }
            }
            for(int stage:new int[]{Integer.MIN_VALUE,-1,10,Integer.MAX_VALUE})check(radius(current,stage)==(stage<0?15:80),"Out of range stage clamped"+stage);
        }
        String result="{\"checks\":"+checks+",\"failures\":"+failures+",\"failed\":\""+failed.toString()+"\",\"minecraftStarted\":false,\"oldAndNewJarsExecutedInIsolatedClassloaders\":true}\n";
        Files.writeString(Path.of(args[2]),result);System.out.print(result);if(failures!=0)throw new AssertionError(failed.toString());
    }
}
