import java.net.*;
import java.io.*;
import java.util.BitSet;

public class Klient implements Runnable {

    private int id;
    private InetAddress ip;
    private int port;
    private Serwer ser;
    DatagramPacket pakiet = new DatagramPacket(new byte[256], 256);
    boolean warunek = true;
    DatagramSocket socket;

    Klient(DatagramSocket socket, Serwer ser) {
        this.socket = socket;
        this.ser = ser;
    }

    public void run() {
        while (warunek){
            try{
                socket.receive(pakiet);
                if(pakiet.getLength() > 0){
                   ser.decode(pakiet);
                }
            }
            catch (IOException e){System.err.println(e.getMessage());}
        }
    }

}
