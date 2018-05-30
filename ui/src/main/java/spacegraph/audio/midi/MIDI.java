package spacegraph.audio.midi;

import javax.sound.midi.*;
import java.util.Arrays;



/**
 * generic MIDI input interface
 */
public class MIDI {

    private final MidiInReceiver receiver;
    float volume[] = new float[128];

    public MIDI() {


        
        MidiDevice device;
        MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();

        MidiInReceiver receiver = null;
        for (int i = 0; i < infos.length; i++) {
            try {
                MidiDevice.Info ii = infos[i];

                device = MidiSystem.getMidiDevice(ii);

                System.out.println(device + "\t" + device.getClass());
                System.out.println("\t" + device.getDeviceInfo());
                System.out.println("\ttx: " + device.getTransmitters());
                System.out.println("\trx: " + device.getReceivers());

                if (receive(device)) {
                    receiver = new MidiInReceiver(device);
                    break;
                }

                /*if (device instanceof Synthesizer) {
                    synthInfos.add((Synthesizer) ii);
                } else if (device instanceof MidiDevice) {
                    midis.add((MidiDevice) ii);
                }*/
            } catch (MidiUnavailableException e) {
                
            }
        }

        this.receiver = receiver;

        Arrays.fill(volume, Float.NaN);

    }


    public static boolean receive(MidiDevice device) {
        return device.getDeviceInfo().getName().startsWith("MPD218");
    }

    public class MidiInReceiver implements Receiver {

        

        private final MidiDevice device;

        public MidiInReceiver(MidiDevice device) throws MidiUnavailableException {
            this.device = device;

            if (!device.isOpen()) {
                device.open();
            }

            device.getTransmitter().setReceiver(this);
        }

        @Override
        public void send(MidiMessage m, long timeStamp) {


            if (m instanceof ShortMessage) {
                ShortMessage s = (ShortMessage) m;
                int cmd = s.getCommand();
                switch (cmd) {
                    case ShortMessage.NOTE_OFF:
                        if ((volume[s.getData1()] == volume[s.getData1()]) && (volume[s.getData1()] > 0))
                            volume[s.getData1()] = 0;




                        
                        break;
                    case ShortMessage.NOTE_ON:
                        volume[s.getData1()] = 0.6f + 0.4f * s.getData2() / 128f;



                        
                        
                        break;
                    default:
                        
                        break;
                    
                }
            }

        }











        @Override
        public void close() {

        }
    }





}
