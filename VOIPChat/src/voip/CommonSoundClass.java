package voip;
import java.util.*;

public class CommonSoundClass {
    @SuppressWarnings("rawtypes")
	public Vector vec = new Vector();
    boolean lock = true;
    private byte b[];


    public CommonSoundClass() {
    }

    synchronized public Object readbyte() {
        try {
            while (vec.isEmpty()) {
                wait();
            }
        }
        catch (InterruptedException ie) {
            System.err.println("Error: CommonSoundClass readbyte interrupted");
        }

        if (! vec.isEmpty()) {
            b = (byte[]) vec.remove(0);
            return b;
        } else {
            byte[] b = new byte[5];
            return b;
        }

    }

    @SuppressWarnings("unchecked")
	synchronized public void writebyte(Object e) {
        vec.addElement(e);

        lock = false;
        notifyAll();
    }
}