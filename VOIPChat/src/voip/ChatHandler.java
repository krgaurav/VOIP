package voip;
import java.io.IOException;
import java.net.Socket;
import java.util.Vector;

import java.io.*;

public class ChatHandler extends Thread {
    @SuppressWarnings("rawtypes")
	static Vector handlers = new Vector(10); // vector that holds the thread
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private boolean alreadyclosed = false;

    private String nick = "";
    private boolean IAmTalking = false;
    private boolean IAmAdmin = false;
    private boolean IAmMute = false;
    private int ImTalkingCounter = 0;

    ServerSendThread s = null;

    private byte[] breaker = MultiChatConstants.BREAKER.getBytes();

    boolean keepGoing = false;


            public ChatHandler() {
    }

    public ChatHandler(Socket socket) throws IOException {
        try {
            this.socket = socket;
        } catch (Exception exp) {
            System.out.println("someone connecting with ftp eh?");
        }
    }

    CommonSoundClass cs = new CommonSoundClass();

    boolean lastpacketrecieved = true;

    public class pingClass extends Thread {
        ChatHandler ptrtoThis = null;

        public pingClass(ChatHandler ptrtoThis) {
            this.ptrtoThis = ptrtoThis;
        }

        public void run() {
            while ( keepGoing ) {
                ptrtoThis.cs.writebyte(("PT$" + MultiChatConstants.BREAKER).getBytes());
                try {
                    synchronized (this) {
                        wait(1000);
                    }
                } catch (Exception mexp) {
                    mexp.printStackTrace();
                }
            }
        }
    }

    // TODO: add more code to this thread to stop the inner while loop or it will wait forever?
    public class ServerSendThread extends Thread {
        ChatHandler fr;

        public ServerSendThread(ChatHandler fr) {
            this.fr = fr;
        }

        public void stopit() {
            keepGoing = false;
        }

        public void run() {
            try {
                while (keepGoing) {
                    lastpacketrecieved = false;
					byte[] b = (byte[]) cs.readbyte();
					//synchronized( handlers ){
					fr.out.write(b);
					fr.out.flush();
					//}
                }
            } catch (Exception exp) {
                //exp.printStackTrace();
                System.out.println("nothing to worry about");
            } finally {
                try {
                    synchronized (handlers) {

                        // tell others that this user logged off
                        String msg = "NC" + fr.nick + MultiChatConstants.BREAKER;
                        broadcastMsg(msg);

                        if (IAmTalking) { // if i'm talking
                            msg = "NT" + fr.nick + MultiChatConstants.BREAKER;
                            broadcastMsg(msg);
                        }

                        IAmTalking = false;
                        keepGoing = false;
                        if (s != null) {
                            fr.s.stopit(); // stop the thread from sending any more data to me i'm already logged off
                        }

                        if (! alreadyclosed) {
                            alreadyclosed = true;
                            in.close();
                            out.close();
                            socket.close();
                        }
                    }
                } catch (IOException ioe) {
                } finally {
                    synchronized (handlers) {
                        if (fr.nick != null && fr.nick != "") {
                            System.out.println(fr.nick + " signed off");
                        }
                        keepGoing = false;
                        handlers.removeElement(fr);

                    }
                }
            }
        }

        private void broadcastMsg(String msg) {
            for (int i = 0; i < handlers.size(); i++) {
                ChatHandler tmp = (ChatHandler) (handlers.elementAt(i));
                if (tmp != fr) {
                    tmp.cs.writebyte(msg.getBytes());
                    //tmp.out.flush();
                }
            }
        }
    }

    public class timeout extends Thread {
        ChatHandler fr;
        public boolean shuldclose = true;

        public timeout(ChatHandler fr) {
            this.fr = fr;
        }

        public void run() {
            try {
                sleep(9000);
                if (shuldclose && !alreadyclosed) {
                    alreadyclosed = true;
                    if (fr.in != null) {
                        fr.in.close();
                    }
                    if (fr.out != null) {
                        fr.out.close();
                    }
                    if (fr.socket != null) {
                        fr.socket.close();
                    }
                }
            } catch (Exception exp) {
            }
        }
    }

    public class vectorandsize implements Serializable {
        /**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		public byte[] b;
        public int size;

        public vectorandsize(byte[] b, int size) {
            this.b = b;
            this.size = size;
        }
    }

    @SuppressWarnings("rawtypes")
	Vector recievedByteVector = new Vector();

    /**
     * This thread handles processing of received items that were in the received byte vector
     */
    public class ReceivedQueueProcessorThread extends Thread {
        ChatHandler ptrtoThis = null;

        public ReceivedQueueProcessorThread(ChatHandler ptrtoThis) {
            this.ptrtoThis = ptrtoThis;
        }

        public void run() {
            while ( keepGoing ) {
                if (recievedByteVector.size() > 0) {
                    vectorandsize vs = (vectorandsize) recievedByteVector.remove(0);
                    byte[] bytepassedObj = vs.b;
                    int sizeread = vs.size;

                    processData(sizeread, bytepassedObj);
                } else {
                    try {
                        synchronized (this) {
                            wait(10);
                        }
                    } catch (Exception mexp) {
                        mexp.printStackTrace();
                    }
                }
            }
        }

        private void processData(int sizeread, byte[] bytepassedObj) {
            synchronized (handlers) {
                String passedObj = "";
                // Convert data to String since it is probably a command
                // TODO: Checking for size < 100 is not good check of commands / text since messages
                // and nicknames could be larger
                if (sizeread < 100 && sizeread >= 0) {
                    passedObj = new String(bytepassedObj, 0, sizeread);
                }

                byte[] b = new byte[sizeread];
                for (int x = 0 ; x < sizeread; x++) {
                    b[x] = bytepassedObj[x];
                }

                // Nickname added
                if ((sizeread > 2 && sizeread < 100 && passedObj.length() >= 2
                        && passedObj.substring(0, 2).equals("NN")) && ptrtoThis.nick == "") {
                    for (int i = 0; i < handlers.size(); i++) {
                        // if someone is trying to log in with a nick that is already used reject their log in
                        ChatHandler tmp = (ChatHandler) (handlers.elementAt(i)); // this can be used for password authentication as well.
                        if (passedObj.substring(2, passedObj.length() - 5).equals(tmp.nick)) {
                            ptrtoThis.cs.writebyte(("bye" + MultiChatConstants.BREAKER).getBytes());
                            //this.out.write( ("bye").getBytes() );
                            //this.out.flush();
                            keepGoing = false;
                        }
                    }

                    if (keepGoing) {
                        pingClass pc = new pingClass(ptrtoThis);
                        pc.start();

                        if (passedObj.length() > 6) {
                            nick = passedObj.substring(2, passedObj.length() - 5);
                            System.out.println(nick + " signed on.");
                        }

                        if (nick.equals("admin")) { // if you log in with this nick name you will be admin
                            IAmAdmin = true;
                        }

                        for (int i = 0; i < handlers.size(); i++) {
                            ChatHandler tmp = (ChatHandler) (handlers.elementAt(i));
                            if (tmp != ptrtoThis && tmp.nick != "" && ptrtoThis.nick != "") {
                                // everyone elses nick name to me
                                ptrtoThis.cs.writebyte(("NN" + tmp.nick + MultiChatConstants.BREAKER).getBytes());
                                //this.out.write( ("NN" + tmp.nick).getBytes() );
                                //this.out.flush();
                            }
                        }

                        for (int i = 0; i < handlers.size(); i++) {
                            ChatHandler tmp = (ChatHandler) (handlers.elementAt(i));
                            if (tmp != ptrtoThis && tmp.nick != "" && ptrtoThis.nick != "") {
                                // ny nick name to everyone else
                                tmp.cs.writebyte(("NN" + ptrtoThis.nick + MultiChatConstants.BREAKER).getBytes());
                                //tmp.out.write( ("NN" + this.nick).getBytes() );
                                //tmp.out.flush();
                            }
                        }
                    }
                //stopped talking
                } else if (sizeread > 2 && sizeread < 100 && passedObj.length() >= 2
                        && (passedObj.substring(0, 2).equals("NT") || passedObj.substring(0, 2).equals("#&"))) {
                    if (IAmTalking == true) {
                        for (int i = 0; i < handlers.size(); i++) {
                            ;
                            ChatHandler tmp = (ChatHandler) (handlers.elementAt(i));
                            tmp.cs.writebyte(("NT" + ptrtoThis.nick + MultiChatConstants.BREAKER).getBytes());
                            //tmp.out.write( ("NT" + this.nick).getBytes() );
                            //tmp.out.flush();
                        }
                        ImTalkingCounter = 0;//reset the talk counter so next time someone taks it could notify them that someone is talking
                        IAmTalking = false;
                    }
                //notify other people that nobody is talking
                } else if (sizeread >= 2 && sizeread < 100 && passedObj.length() >= 2 && (passedObj.substring(0, 2).equals("PR"))) {
                    //packet recieved send next one
                    lastpacketrecieved = true;
                // Mute one of the users
                } else if (sizeread > 2 && sizeread < 100 && passedObj.length() > 4 && passedObj.substring(0, 4).equals("MUTE")) {
                    if (IAmAdmin) {
                        for (int i = 0; i < handlers.size(); i++) {
                            ;
                            ChatHandler tmp = (ChatHandler) (handlers.elementAt(i));
                            if (tmp.nick.equals(passedObj.substring(4, passedObj.length() - 5))) {
                                //send the user a message that he is mute
                                tmp.cs.writebyte(("MUTE" + ptrtoThis.nick + MultiChatConstants.BREAKER).getBytes());
                                //tmp.out.write( ("MUTE" + this.nick).getBytes() );
                                //tmp.out.flush();

                                tmp.IAmMute = true;
                                if (tmp.IAmTalking == true) { // tell everyone that they can now talk
                                    for (int j = 0; j < handlers.size(); j++) {
                                        ChatHandler tmp2 = (ChatHandler) (handlers.elementAt(i));
                                        tmp2.cs.writebyte(("NT" + ptrtoThis.nick + MultiChatConstants.BREAKER).getBytes());
                                        //tmp2.out.write( ("NT" + this.nick).getBytes() );
                                        //tmp2.out.flush();

                                    }
                                    ImTalkingCounter = 0;//reset the talk counter so next time someone taks it could notify them that someone is talking
                                    tmp.IAmTalking = false;
                                }
                            }
                        }
                    }
                // UnMute one of the users
                } else if (sizeread > 2 && sizeread < 100 && passedObj.length() > 6 && passedObj.substring(0, 6).equals("UNMUTE")) {
                    if (IAmAdmin) {
                        for (int i = 0; i < handlers.size(); i++) {
                            ;
                            ChatHandler tmp = (ChatHandler) (handlers.elementAt(i));
                            if (tmp.nick.equals(passedObj.substring(6, passedObj.length() - 5))) {
                                //send the user a message that he is no longer mute
                                tmp.cs.writebyte(("UNMUTE" + ptrtoThis.nick + MultiChatConstants.BREAKER).getBytes());
                                //tmp.out.write( ("UNMUTE" + this.nick).getBytes() );
                                //tmp.out.flush();
                                tmp.IAmMute = false;
                            }
                        }
                    }
                //text talking or low audio size (SW: Probably not low audi of starts with TXT?)
                } else if (sizeread > 2 && sizeread < 100 && passedObj.substring(0, 3).equals("TXT")) {
                    for (int i = 0; i < handlers.size(); i++) {
                        ChatHandler tmp = (ChatHandler) (handlers.elementAt(i));
                        if (tmp != ptrtoThis || passedObj.length() >= 3 && passedObj.substring(0, 3).equals("TXT")) {
                            tmp.cs.writebyte(b);

                        }
                    }
                // Audio

                } else {
                    boolean someoneIsTalking = false;
                    String talkersNick = "";
                    for (int i = 0; i < handlers.size(); i++) {
                        ChatHandler tmp = (ChatHandler) (handlers.elementAt(i));
                        if (tmp.IAmTalking == true) {
                            someoneIsTalking = true;
                            talkersNick = tmp.nick;
                        }
                    }
                    if ((handlers.size() <= 2 || someoneIsTalking == false || IAmTalking == true && ptrtoThis.nick != "")
                            && (IAmMute == false)) {
                        if (ImTalkingCounter % 8 == 0 /*|| true*/) {
                            //make sure there are more then two people in the room
                            for (int i = 0; i < handlers.size(); i++) {
                                ChatHandler tmp = (ChatHandler) (handlers.elementAt(i));
                                if (tmp.nick != "") {
                                    if (handlers.size() > 2) {
                                        if (nick != "") {
                                            // stop talking, i'm using the mic also notifies the talker

                                            tmp.cs.writebyte(("ST" + nick + MultiChatConstants.BREAKER).getBytes());
                                            
                                        }
                                    } else {
                                        // one on less chat when one user is talking highlight his name

                                        tmp.cs.writebyte(("PT" + nick + MultiChatConstants.BREAKER).getBytes());
                                    }
                                }
                            }
                            ImTalkingCounter = 0;
                        }
                        IAmTalking = true;
                        ImTalkingCounter++; // used to highlight names for people who are talking

                        for (int i = 0; i < handlers.size(); i++) {
                            ChatHandler tmp = (ChatHandler) (handlers.elementAt(i));
                            if (tmp != ptrtoThis && tmp.nick != "") {
                                // anyone whos is not logged in wont hear voice.
                                tmp.cs.writebyte(b);
                            }
                        }
                    } else {
                        if (handlers.size() > 2) { // if there is more then two people in the room
                            if (talkersNick != "") {
                                // stop talking, someone is using the mic
                                ptrtoThis.cs.writebyte(("ST" + talkersNick + MultiChatConstants.BREAKER).getBytes());

                            }
                        } else if (IAmMute) {
                            // stop talking, someone is using the mic

                            ptrtoThis.cs.writebyte(("ST|YourMute" + MultiChatConstants.BREAKER).getBytes());

                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
	public void run() {
        keepGoing = false;
        try {
            out = socket.getOutputStream();
            out.flush();
            s = new ServerSendThread(this);
            timeout t = new timeout(this);
            t.start();
            s.start();
            in = socket.getInputStream();
            t.shuldclose = false;

            keepGoing = true;
        } catch (IOException ignored) {}

        synchronized (handlers) {
            handlers.addElement(this);
        }

        try {
            ReceivedQueueProcessorThread il = new ReceivedQueueProcessorThread(this);
            il.start();

            byte[] mybyte = new byte[1024 * 3];

            int j = 0;
            while ( keepGoing ) {
                byte[] bytepassedObj = new byte[MultiChatConstants.bytesize];
                int sizeread = in.read(bytepassedObj, 0, MultiChatConstants.bytesize);
                //recievedByteVector.addElement( ((Object)(new vectorandsize( bytepassedObj, sizeread ))) );

                for (int i = 0; i < sizeread; i++, j++) {
                    mybyte[j] = bytepassedObj[i];

                    // Read up to 3071 (though we're only currently reading up to 1025 bytes in)
                    // or if this has the breaker at the location we're currently at
                    if (j == (1024 * 3 - 1) || (j >= 4 && mybyte[j - 4] == breaker[0]
                            && mybyte[j - 3] == breaker[1] && mybyte[j - 2] == breaker[2]
                            && mybyte[j - 1] == breaker[3] && mybyte[j] == breaker[4])) {
                        recievedByteVector.addElement(((Object) (new vectorandsize(mybyte, j + 1))));
                        j = -1;
                        // Create a new container
                        mybyte = new byte[1024 * 3];
                    }
                }
            }
        } catch (NullPointerException npx) {
            npx.printStackTrace();
        } catch (IOException ioe) {
            //ioe.printStackTrace();.
        } catch (Exception exp) {
            //ioe.printStackTrace();.
            exp.printStackTrace();
        }
        finally {
            try {
                synchronized (handlers) {
                    // tell others that this user logged off
                    broadcastMessage("NC" + this.nick + MultiChatConstants.BREAKER);

                    if (IAmTalking) { // if i'm talking
                        broadcastMessage("NT" + this.nick + MultiChatConstants.BREAKER);
                    }

                    IAmTalking = false;
                    if (s != null) {
                        this.s.stopit(); // stop the thread from sending any more data to me i'm already logged off
                    }

                    if (! alreadyclosed) {
                        alreadyclosed = true;
                        in.close();
                        out.close();
                        socket.close();
                    }
                }
            } catch (IOException ioe) {
            } finally {
                synchronized (handlers) {
                    if (this.nick != null && this.nick != "") {
                        System.out.println(nick + " signed off.");
                    }
                    keepGoing = false;
                    handlers.removeElement(this);

                }
            }
        }
    }

    private void broadcastMessage(String message) {
        for (int i = 0; i < handlers.size(); i++) {
            ChatHandler tmp = (ChatHandler) (handlers.elementAt(i));
            if (tmp != this) {
                tmp.cs.writebyte((message).getBytes());

            }
        }
    }
}