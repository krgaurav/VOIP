package voip;
import java.io.ByteArrayOutputStream;

import    javax.sound.sampled.DataLine;
import    javax.sound.sampled.TargetDataLine;
import    javax.sound.sampled.AudioFormat;
import    javax.sound.sampled.AudioSystem;
import    javax.sound.sampled.AudioInputStream;
import    javax.sound.sampled.LineUnavailableException;
import    javax.sound.sampled.AudioFileFormat;

public class Recorder extends Thread {
    private TargetDataLine m_line;
    private boolean m_bRecording;
    private boolean m_bQuitting;

    public byte bs[];
    public CommonSoundClass cs;

    boolean onlyonce = false;

    public Recorder(CommonSoundClass csPtr) {
        this.cs = csPtr;

        boolean gotrecordingline = true;

        new ByteArrayOutputStream();
        AudioFormat    audioFormat = null;
        // 8 kHz, 8 bit, mono
        audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, ClientShared.sampleRate, ClientShared.sampleSize, 1, 1, ClientShared.frameRate, false);

       
        DataLine.Info    info = new DataLine.Info(TargetDataLine.class, audioFormat);
        TargetDataLine    targetDataLine = null;

        try {
            // Connect to line
            targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
            targetDataLine.open(audioFormat);
        }
        catch (LineUnavailableException e) {
            System.err.println("Error: Unable to get a recording line");
            gotrecordingline = false;
            //e.printStackTrace();
            //System.exit(1);
        }


        if (gotrecordingline) {
            AudioFileFormat.Type    targetType = AudioFileFormat.Type.AU;
//	        Recorder recorder = null;
            RecorderInit(targetDataLine, targetType);
            m_bRecording = true;
            m_bQuitting = false;
            this.start();
        }
    }


    public void RecorderInit(TargetDataLine line, AudioFileFormat.Type targetType) {
        m_line = line;
        new AudioInputStream(line);
    }


    /**
     * Starts the recording.
     * To accomplish this, (i) the line is started and (ii) the
     * thread is started.
     */
    public void start() {
        m_bRecording = true;
        m_line.start();
        super.start();
    }

    public void startRecording() {
        m_bRecording = true;
    }

    public void stopRecording() {
        m_bRecording = false;
        onlyonce = true;
    }

    synchronized public void run() {
        while ( !m_bQuitting ) {
            byte bs[] = new byte[ClientShared.audioReadBytes];
            m_line.read(bs, 0, ClientShared.audioReadBytes);
            if (m_bRecording) {
                cs.writebyte(bs);
            } else if (onlyonce) {
                cs.writebyte(("NT|").getBytes());
                onlyonce = false;
            }
        }

        m_line.stop();
        m_line.close();
    }

    public void onExit() {
        m_bQuitting = true;
    }
}

