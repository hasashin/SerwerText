import java.net.*;
import java.io.*;

public class Listener implements Runnable {

    private Serwer ser;
    private DatagramPacket pakiet = new DatagramPacket(new byte[256], 256);
    private DatagramSocket socket;
    boolean warunek = true;

    Listener(DatagramSocket socket, Serwer ser) {
        this.socket = socket;
        this.ser = ser;
        try{
            this.socket.setSoTimeout(50);
        }
        catch(IOException e){
            System.err.println(e.getMessage());
        }
    }

    public void run() {
        while (warunek){
            try{

                socket.receive(pakiet);
                ser.decode(pakiet);

            }
            catch (IOException e){

                if(e.getMessage().equals("Receive timed out")) {
                    continue;
                }
                else if(e.getMessage().equals("Socket closed")){
                    warunek = false;
                    break;
                }
                System.err.println(e.getMessage());
            }
        }
    }

}
