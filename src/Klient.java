import java.net.*;
import java.io.*;
import java.util.BitSet;

public class Klient implements Runnable {

    private int id;
    private InetAddress ip;
    private int port;
    private Serwer ser;
    private byte[] pakiet = new byte[4];
    private boolean warunek = true;

    Klient(DatagramPacket pakiet, int clientid, Serwer s) {
            ip = pakiet.getAddress();
            port = pakiet.getPort();
            id = clientid;
            ser = s;
            wyslijpakiet(0, 0, 0, 0);
    }

    private byte[] generujPakiet(int operacja, int odpowiedz, int liczba, int czas) {
        /*

            operacja - 3 bity
            odpowiedź - 3 bity
            identyfiaktor - 5 bitów
            liczba - 8 bitów
            czas - 8 bitów
            dopełnienie - 5 bitów (zera)

            000|000|00 000|00000 000|00000 000|00000
            OP |ODP|  ID  |    L    |   TIME  | DOP

            000|000|00
            000|00000
            000|00000
            000|00000

         */
        byte[] ret = new byte[4];

        ret[0] = (byte) ((operacja & 0b00000111) << 5);
        ret[0] = (byte) (ret[0] | (byte) ((odpowiedz & 0b00000111) << 2));
        ret[0] = (byte) (ret[0] | (byte) ((id & 0b00011000) >> 3));

        ret[1] = (byte) ((id & 0b00000111) << 5);
        ret[1] = (byte) (ret[1] | (byte) ((liczba & 0b11111000) >> 3));

        ret[2] = (byte) ((liczba & 0b00000111) << 5);
        ret[2] = (byte) (ret[2] | (byte) ((czas & 0b11111000) >> 3));

        ret[3] = (byte) ((czas & 0b00000111) << 5);

        return ret;
    }



    private void zakoncz() {
        try {
            clientsocket.close();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        } finally {
            warunek = false;
        }
    }

    private void execute(int operacja, int odpowiedz, int liczba) {

        if (operacja == 3 && odpowiedz == 0) {
            ser.sprawdz(liczba, this);
        }
        if (operacja == 7 && odpowiedz == 7) {
            System.out.println("Klient " + id + " kończy połączenie");
            zakoncz();
        }
    }

    private void decode(byte[] data) {
        int odpowiedz, sesja, operacja, liczba;

        operacja = (data[0] & 0b11100000) >> 5;
        odpowiedz = (data[0] & 0b00011100) >> 2;
        sesja = ((data[0] & 0b00000011) << 3) | ((data[1] & 0b11100000) >> 5);
        liczba = ((data[1] & 0b00011111) << 3) | ((data[2] & 0b11100000) >> 5);

        if (sesja == id) {
            execute(operacja, odpowiedz, liczba);
        } else {
            System.out.println("Odebrano niepoprawny komunikat od klienta " + id);
        }
    }

    public void run() {
        int len;
        while (warunek) {
            try {
                len = in.read(pakiet);            //  odpowiedz
                if (len == -1) {
                    System.out.println("Klient " + id + " rozłączył się");
                    warunek = false;
                    break;
                } else decode(pakiet);
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }


    }

}
