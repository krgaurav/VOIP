package voip;
import java.util.*;

public class Queue {
    // Internal storage for the queue'd objects
    @SuppressWarnings("rawtypes")
	private Vector vec = new Vector();
    boolean prebuffer = true;
    // Lower this rate for quicker delivery of audio
    int queueWaitSize = 12;/*16 * 5 + 1*/
    
    synchronized public int numWaiting() {
    	// makes the client wait for more packets before starting to play them
    	// this takes care of the buffer :)
        // This makes sound clearer but also causes delays in receiving audio.        
        if( prebuffer && vec.size() < queueWaitSize){
    		return 0;
    	} else {
    		prebuffer = false;
        	return vec.size();
    	}
    }
    
    @SuppressWarnings("unchecked")
	synchronized public void put( Object o ) {
        // Add the element
        vec.addElement( o );
        // There might be threads waiting for the new object --
        // give them a chance to get it
        notifyAll();
    }
    
    synchronized public Object get() {
        while (true) {
            if ( numWaiting() > 0 /*vec.size()>0*/ ) {
            	
            	// remove the bytes if its more then 1.5 seconds delay
            	while( vec.size() > (24 /*16 * 10*/) ){
            		vec.removeElementAt(0);
        		}
            	
                // There's an available object!
                Object o = vec.elementAt( 0 );
                
                // Remove it from our internal list, so someone else
                // doesn't get it.
                

                
                /*if( vectorsize > 1 ){
                	vectorsize = 1;
                }
                
                int j = 0;
                int i = 0;
                byte[] bigbyte = new byte[vectorsize * (1024*8)];
                for(; i < vectorsize * 512; i++){
                	if( j == 1024*8 ){
                		j = 0;
                		vec.removeElementAt(0);
                	}
                	bigbyte[i] = ((byte[])(vec.elementAt( 0 )))[j];
                	j++;
                }*/
                
                
                vec.removeElementAt( 0 );
                

                if( vec.size() == 0 ){
                	prebuffer = true; // we have reached the last element in the stack we shuld buffer more data before playing
                }
                
                // Return the object
                return o;
                //return (Object)bigbyte;
            } else {
                // There aren't any objects available.  Do a wait(),
                // and when we wake up, check again to see if there
                // are any.
                try { wait(); } catch( InterruptedException ie ) {}
            }
        }
    }
}
