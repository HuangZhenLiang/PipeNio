import java.nio.channels.Pipe;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.PipedOutputStream;
import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.ByteArrayInputStream;

class Person implements Serializable{
	private static final long serialVersionUID=800000000L;
	private String name;
	public Person(String name){
		this.name=name;
	}
	public String getName(){
		return name;
	}
}

class WorkThread implements Runnable{
	private Pipe.SinkChannel sinkChannel=null;
	private String workName=null;
	private PipeTest test=null;
	public WorkThread(Pipe.SinkChannel sinkChannel,String workName,PipeTest test){
		this.sinkChannel=sinkChannel;
		this.workName=workName;
		this.test=test;
	}
	@Override
	public void run(){
		
		try {
			ByteArrayOutputStream baos=new ByteArrayOutputStream(1024);
			ObjectOutputStream oos=new ObjectOutputStream(baos);
			oos.writeObject(new Person(workName));
			//oos.writeObject(new Person(workName));
			oos.flush();
			
		ByteBuffer buf=ByteBuffer.allocate(1024);
		buf.put(baos.toByteArray());
		buf.flip();
		while(buf.hasRemaining()){
			try {
				sinkChannel.write(buf);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		oos.close();
		baos.close();
		}
		catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}finally{
			test.countDown();
			
		}
	}
	
}
public class PipeTest {
	private AtomicInteger flag=new AtomicInteger(2);
	
	public void countDown(){
		flag.decrementAndGet();
	}
	public int getFlag(){
		return flag.get();
	}
	public static void run() throws IOException{
		Pipe p=Pipe.open();
		Pipe.SinkChannel ps=p.sink();
		ByteBuffer buf=ByteBuffer.allocate(1024);
		buf.put(new String("hello,world").getBytes());
		buf.flip();
		
		while(buf.hasRemaining()){
			ps.write(buf);
		}
		
		ByteBuffer buf2=ByteBuffer.allocate(1024);
		Pipe.SourceChannel source=p.source();
		int len=source.read(buf2);
		
		source.close();
		ps.close();
	}
	public static void main(String[] args) throws IOException, ClassNotFoundException{
		
		Pipe pipe=Pipe.open();
		Pipe.SinkChannel sink=pipe.sink();
		Pipe.SourceChannel s=pipe.source();
		Selector selector=Selector.open();
		s.configureBlocking(false);
		s.register(selector, SelectionKey.OP_READ);
		PipeTest p=new PipeTest();
		new Thread(new WorkThread(sink,"work 1",p)).start();
		new Thread(new WorkThread(sink,"work 2",p)).start();
		while(true){
			int len=selector.select();
			if(len==0)
				continue;
			Iterator it=selector.selectedKeys().iterator();
			
			while(it.hasNext()){
				SelectionKey key=(SelectionKey)it.next();
				if(key.isReadable()){
					System.out.println("read from thread");
					Pipe.SourceChannel sc=(Pipe.SourceChannel)key.channel();
					ByteBuffer buf=ByteBuffer.allocate(1024);
					int count=0;
					while((count=sc.read(buf))>0){
						buf.flip();
						byte[] tmpbuf=new byte[count+2];
						System.arraycopy(buf.array(), 0, tmpbuf, 0, 64);
						tmpbuf[64]=(byte)112;//设置结束标志位
						System.arraycopy(buf.array(), 64, tmpbuf, 65, 64);
						tmpbuf[count+1]=(byte)112;//设置结束标志位
						ByteArrayInputStream bais=new ByteArrayInputStream(tmpbuf,0,65);
						ObjectInputStream ois=new ObjectInputStream(bais);
						Object object=null;
						while((object=ois.readObject())!=null){
							System.out.println(((Person)object).getName());
						}
						ByteArrayInputStream bais1=new ByteArrayInputStream(tmpbuf,65,count+2);
						ObjectInputStream ois1=new ObjectInputStream(bais1);
						while((object=ois1.readObject())!=null){
							System.out.println(((Person)object).getName());
						}
						buf.clear();
					}
					if(count<0)
						sc.close();
				}
				it.remove();
			}
			if(p.getFlag()==0)
				break;
			
		}
		selector.close();
		sink.close();
		s.close();
		
	}

}
