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

        //pętla nasłuchująca komunikaty
        while (warunek){
            try{

                //odbieranie pakietów
                socket.receive(pakiet);

                //dekodowanie pakietów
                ser.decode(pakiet);

            }
            catch (IOException e){

                //ignorowanie błędów dotyczących czasu oczekiwania na odpowiedź
                if(e.getMessage().equals("Receive timed out")) {
                    continue;
                }

                //po zamknięciu gniazda pętla nasłuchująca kończy pracę
                else if(e.getMessage().equals("Socket closed")){
                    warunek = false;
                    break;
                }
                System.err.println(e.getMessage());
            }
        }
    }

}
