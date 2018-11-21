import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;


class Serwer {

    private int id1;
    private int id2;
    private int czasrozgrywki;
    private long poczatkowy;
    private int liczba;
    private boolean warunek = true;
    private ServerSocket socket;
    private Klient k1;
    private Klient k2;


    Serwer(int port) {
        try {
            socket = new ServerSocket(port);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private void generuj() {
        Random generator = new Random();
        id1 = (generator.nextInt(30) + 1);
        id2 = (generator.nextInt(30) + 1);
    }

    private void maxczas() {
        czasrozgrywki = (Math.abs(id1 - id2) * 74) % 90 + 24;
    }

    private void losujliczbe() {
        Random generator = new Random();
        liczba = (generator.nextInt(254)+1);
        System.out.println("Wybrano " + liczba);
    }

    void sprawdz(int odp, Klient k) {
        if (odp == liczba) {
            k.wyslijpakiet(7, 1, 0, 0);

            if (k.equals(k1)) {
                k2.wyslijpakiet(7, 0, liczba, 0);
            } else {
                k1.wyslijpakiet(7, 0, liczba, 0);
            }
        } else {
            if(odp < liczba)
                k.wyslijpakiet(3, 1, 0, 0);
            else
                k.wyslijpakiet(3, 4, 0, 0);
        }
    }

    private void ileczasu() {
        long obecny = System.currentTimeMillis() / 1000;
        long uplynelo = obecny - poczatkowy;
        long zostalo = czasrozgrywki - uplynelo;
        if (zostalo > 0) {
            System.out.println("Zostalo " + zostalo + " sekund");
            k1.wyslijpakiet(3, 2, 0, (int) zostalo);
            k2.wyslijpakiet(3, 2, 0, (int) zostalo);
        } else {
            k1.wyslijpakiet(7, 2, 0, 0);
            k2.wyslijpakiet(7, 2, 0, 0);
            warunek = false;
        }

    }

    void start() {
        generuj();
        System.out.println("Oczekiwanie na klientów...");
        k1 = new Klient(socket, id1, this);
        System.out.println("Połączono 1/2, id " + id1);
        k2 = new Klient(socket, id2, this);
        System.out.println("Połączono 2/2, id " + id2);

        System.out.println("Przygotowywanie gry");
        //poczatkowyczas = LocalTime.now();
        maxczas();
        losujliczbe();
        poczatkowy = System.currentTimeMillis() / 1000;
        //ileczasu();

        try {
            socket.close();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        Thread f1 = new Thread(k1);
        Thread f2 = new Thread(k2);

        k1.wyslijpakiet(2, 0, 0, 0);
        k2.wyslijpakiet(2, 0, 0, 0);

        f1.start();
        f2.start();

        System.out.println("Start");

        long pietnascie = System.currentTimeMillis() / 1000;

        while (warunek) {
            if ((System.currentTimeMillis() / 1000 - pietnascie) > 14) {
                ileczasu();
                pietnascie = System.currentTimeMillis() / 1000;
            }
            if ((poczatkowy * czasrozgrywki) - System.currentTimeMillis() / 1000 <= 0) {
                ileczasu();
            }
            if (!f1.isAlive() && !f2.isAlive()) {
                warunek = false;
            }
        }

        f1.interrupt();
        f2.interrupt();


    }

}