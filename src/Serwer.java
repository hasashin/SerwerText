import java.io.IOException;
import java.net.*;
import java.sql.Timestamp;
import java.util.*;


class Serwer {

    //klasa par numerów id klientów i ich adresów w formacie: "IP:PORT"
    class Pair {
        final Integer key;
        final String value;

        Pair(Integer x, String y) {
            this.key = x;
            this.value = y;
        }
    }


    private int delID;
    private boolean del = false;
    private boolean ingame = false;
    private int czasrozgrywki;
    private long poczatkowy;
    private int liczba;
    private DatagramSocket socket;
    private Vector<Pair> klienci = new Vector<>();
    private Listener listener;
    private Thread listenThread;
    private Timestamp timestamp;


    Serwer(int port) {
        try {
            //tworzymy nowe gniazdo na ustalonym porcie
            socket = new DatagramSocket(port);

            //tworzymy obiekt nasłuchujący
            listener = new Listener(socket,this);

            //tworzymy obiekt generujący znaczniki czasu
            timestamp = new Timestamp(System.currentTimeMillis());
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private boolean isClientConnected(String address){

        //pobieramy adres IP i port klienta
        String[] inetData = address.split(":");

        boolean ret;
        try{

            //próbujemy zająć gniazdo klienta
            DatagramSocket soc = new DatagramSocket(Integer.parseInt(inetData[1]),InetAddress.getByName(inetData[0]));

            //jeśli się to powiedzie oznacza, że klient nie jest podłączony
            //ustawiamy zmienną do zwrócenia i zamykamy gniazdo
            ret = false;
            soc.close();
        }
        catch(IOException e){

            //jesli nie powiedzie się stworzenie gniazda oznacza, że klient jest podłączony
            //(ewentualnie inny program zajął to gniazdo)
            ret = true;
        }
        return ret;
    }

    private int getIdByPacket(DatagramPacket dat) {

        //pobieramy identyfikator klienta na podstawie jego adresu i portu(wersja z datagramem)
        for (Pair elem : klienci) {
            if(elem.value.equals(dat.getAddress().getHostAddress()+":"+dat.getPort())){
                return elem.key;
            }
        }
        return 0;
    }

    private int getIdByPacket(String inetData){

        //pobieramy identyfikator klienta na podstawie jego adresu i portu(wersja z tekstem)
        for (Pair elem : klienci){
            if(elem.value.equals(inetData)){
                return elem.key;
            }
        }
        return 0;
    }

    private String getStringFromID(int id){

        //pogranie adresu klienta, kiedy znamy jego identyfikator
        for(Pair elem : klienci){
            if(elem.key == id){
                return elem.value;
            }
        }
        return null;
    }

    private int generuj() {

        //generowanie numeru klienta
        int numerklienta;
        Random generator = new Random();
        numerklienta = (generator.nextInt(254) + 1);

        for (Pair elem : klienci) {
            if (numerklienta == elem.key) {
                return 0;
            }
        }
        return numerklienta;
    }

    private void maxczas() {

        //obliczanie czasu gry na podstawie identyfikatorów wdóch pierwszych klientów
        int id1 = klienci.get(0).key;
        int id2 = klienci.get(1).key;

        czasrozgrywki = (Math.abs(id1 - id2) * 99) % 100 + 30;
    }

    private void losujliczbe() {

        //losowanie liczby tajnej
        Random generator = new Random();
        liczba = (generator.nextInt(254) + 1);
        System.out.println("Wybrano " + liczba);
    }

    private void broadcast(String operacja, String odpowiedz, int czas) {

        //wysłanie wszystkim klientom tego samego komuniaktu
        Pair[] arr = new Pair[klienci.size()];
        klienci.toArray(arr);
        for (Pair elem : arr) {
            //pobranie adresu i portu klienta
            String[] addr = elem.value.split(":");
            try {
                //wysłanie pakietu do klienta
                wyslijpakiet(operacja, odpowiedz, elem.key, 0, czas, InetAddress.getByName(addr[0]), Integer.parseInt(addr[1]));
            }catch (IOException e){
                //jeśli wystąpi błąd w trakcie wysyłania jest on wyświetlony
                System.err.println(e.getMessage());
            }
        }
    }

    private void sprawdz(int odp, DatagramPacket dat) {

        //sprawdzanie odpowiedzi od klienta
        if (odp == liczba) {
            ingame = false;

            //wysyłamy zgadującemu graczowi informację o wygranej
            wyslijpakiet("end", "wygrana", getIdByPacket(dat), liczba, 0, dat.getAddress(), dat.getPort());

            //wysyłamy pozostałym informację o przegranej
            for(Pair elem : klienci){
                if(!elem.value.equals(dat.getAddress().getHostAddress()+":"+dat.getPort())){
                    String[] inet = elem.value.split(":");
                    try{
                        wyslijpakiet("end","przegrana",getIdByPacket(elem.value),liczba,0,InetAddress.getByName(inet[0]),Integer.parseInt(inet[1]));
                    }catch(IOException e){
                        System.err.println(e.getMessage());
                    }
                }
            }
        } else {
            //wysyłamy zgadującemy klientowi inforamcję o niepoprawnej liczbie
            if (odp > liczba)
                wyslijpakiet("notify", "duza", getIdByPacket(dat), 0, 0, dat.getAddress(), dat.getPort());
            else
                wyslijpakiet("notify", "mala", getIdByPacket(dat), 0, 0, dat.getAddress(), dat.getPort());
        }
    }

    private void ileczasu() {

        //liczymy czas gry
        long obecny = System.currentTimeMillis() / 1000;
        long uplynelo = obecny - poczatkowy;
        int zostalo = (int) (czasrozgrywki - uplynelo);

        //powiadamiamy klientów o pozostałym czasie
        if (zostalo > 0) {
            System.out.println("Zostalo " + zostalo + " sekund");
            broadcast("notify", "czas", zostalo);
        } else {

            //powiadamiamy klientów o zakończeniu czasu gry
            broadcast("end", "koniecCzasu", 0);
        }

    }

    private DatagramPacket generujPakiet(String operacja, String odpowiedz, int id, int liczba, int czas, InetAddress ip, int port) {

        //tworzenie bufora datagramu
        byte[] buff = new byte[256];

        //tworzenie nowego datagramu
        DatagramPacket pakiet = new DatagramPacket(buff, 256);

        //budowanie komunikatu
        String komunikat = "";

        komunikat += "OP?" + operacja + "<<";
        komunikat += "OD?" + odpowiedz + "<<";
        komunikat += "ID?" + id + "<<";
        komunikat += "LI?" + liczba + "<<";
        komunikat += "CZ?" + czas + "<<";
        komunikat += "TS?" + timestamp.getTime() +"<<";
        //komunikat += "\0";

        //przypisanie gomunikatu do bufora datagramu
        pakiet.setData(komunikat.getBytes());

        //ustawienie adresu i portu adresata
        pakiet.setAddress(ip);
        pakiet.setPort(port);

        return pakiet;
    }

    private void wyslijpakiet(String operacja, String odpowiedz, int id, int liczba, int czas, InetAddress ip, int port) {
        try {
            //sprawdzamy czy klient jest podłączony
            if(isClientConnected(ip.getHostAddress()+":"+port))
                //wysyłamy temu klientowi komunikat
                socket.send(generujPakiet(operacja, odpowiedz, id, liczba, czas, ip, port));
            else {
                if(ingame) {

                    //powiadamiamy użytkownika, że klient się rozłączył w trakcie gry
                    System.out.println("Klient " + id + " rozłączył się");
                    zakoncz(id);
                }
            }
        } catch (IOException r) {
            System.err.println(r.getMessage());
        }

    }

    void decode(DatagramPacket pakiet) {
        int liczba, id;

        //rozdzielenie pól komunikatu
        String[] options = new String(pakiet.getData()).split("<<");

        Hashtable<String, String> optionsSplit = new Hashtable<>();

        //dodanie dwuelementowych pól (klucz i wartość) do tablicy
        for (String elem : options) {
            String[] temp = elem.split("[?]");
            if (temp.length == 2)
                optionsSplit.put(temp[0], temp[1]);
        }

        //kopiowanie wartości przysłanych do lokalnych zmiennych
        liczba = Integer.parseInt(optionsSplit.get("LI"));
        id = Integer.parseInt(optionsSplit.get("ID"));

        //sprawdzamy czy przysłane id jest wśród zapisanych klientów
        boolean exists = false;
        for (Pair elem : klienci) {
            if (elem.key == id) {
                exists = true;
                break;
            }
        }

        //jeśli id jest poprawne dekodujemy kominukat
        if (exists || id == 0) {
            execute(optionsSplit.get("OP"), optionsSplit.get("OD"), liczba, id, pakiet);
        } else {
            System.out.println("Odebrano niepoprawny komunikat od klienta "+id);
        }

    }

    private void execute(String operacja, String odpowiedz, int liczba, int id, DatagramPacket pakiet) {

        //jeśli przysłany komuniakt nie jest potwierdzeniem odbioru, wysyłamy takie potwierdzenie
        if (!operacja.equals("response") && !odpowiedz.equals("ACK")) {
            wyslijpakiet("response", "ACK", id, 0, 0, pakiet.getAddress(), pakiet.getPort());
            try{

                //robimy przerwę, żeby komunikaty się nie prześcigały
                Thread.sleep(100);
            }catch(InterruptedException e){
                System.err.println(e.getMessage());
            }
        }

        //klient przysyła liczbę zgadywaną
        if (operacja.equals("notify") && odpowiedz.equals("liczba")) {
            sprawdz(liczba, pakiet);
        }

        //klient kończy połączenie
        if (operacja.equals("end") && odpowiedz.equals("zakonczPol")) {
            System.out.println("Klient " + id + " kończy połączenie");

            //zlecamy usunięcie klienta
            //jeśli jakiś klient jest usuwany to czekamy
            while(del){
                try{
                    Thread.sleep(10);
                }catch(InterruptedException e){
                    System.err.println(e.getMessage());
                }
            }

            //ustawiamy identyfikator klienta do usunięcia i flagę usuwania
            delID = id;
            del = true;
        }

        //klient żąda połączenia
        if (operacja.equals("connect") && odpowiedz.equals("chce")) {
            id = generuj();
            System.out.println("Klient " + id + " połączył się");
            wyslijpakiet("answer", "accept", id, 0, 0, pakiet.getAddress(), pakiet.getPort());
            DatagramPacket pak = new DatagramPacket(new byte[256], 256, pakiet.getAddress(), pakiet.getPort());
            klienci.add(new Pair(id, pak.getAddress().getHostAddress()+":"+pak.getPort()));

            //wysyłamy start gry:
            //wszystkim, jeśli dołączył drugi klient
            //tylko nowemu klientowi, jeśli dołącza trzeci lub kolejny klient
            if(klienci.size() >= 2) {
                if(!ingame){
                    broadcast("start","start",0);
                    ingame = true;
                }else {
                    try {
                        String str;
                        if ((str = getStringFromID(id)) != null) {
                            String[] inetData = str.split(":");
                            wyslijpakiet("start", "start", id, 0, 0, InetAddress.getByName(inetData[0]), Integer.parseInt(inetData[1]));
                        } else
                            System.err.println("Nieznany adres klienta " + id);
                    } catch (Throwable e) {
                        System.err.println(e.getMessage());
                    }
                }
            }
        }

    }

    private void zakoncz(int id) {

        //stworzenie referencji do klienta
        Pair torem = null;
        Pair[] arr = new Pair[klienci.size()];
        klienci.toArray(arr);
        for (Pair elem : arr) {

            //przypisanie klienta, który kończy połączenie
            if (elem.key == id) {
                torem = elem;
            }
        }
        if (torem != null) {

            //usunięcie klienta z tablicy połączonych
            klienci.remove(torem);
            if(klienci.size() == 0)
                klienci = null;
        }

        //wyzerowanie flagi usuwania
        del = false;
        delID = 0;
    }

    private void runGaame() {
        long dziesiec = System.currentTimeMillis() / 1000;
        poczatkowy = System.currentTimeMillis() / 1000;
        System.out.println("Start");
        boolean warunek = true;

        //pętla logiki serwera
        while (warunek) {

            //wyświetlanie pozostałego czasu co 10 s
            if ((System.currentTimeMillis() / 1000 - dziesiec) >= 10) {
                ileczasu();
                dziesiec = System.currentTimeMillis() / 1000;
            }

            //sprawdzanie czy czas gry minął
            if ((poczatkowy * czasrozgrywki) - System.currentTimeMillis() / 1000 <= 0) {
                ileczasu();
            }

            //usuwanie klienta, jeśli zakończył połączenie
            if (del) {
                zakoncz(delID);
            }

            //kończenie pracy, jeśli brak klientów
            if (klienci == null) {
                listener.warunek = false;
                warunek = false;
            }
        }

        try {

            //zamknięcie gniazda i oczekiwanie na zakończenie wątku nasłuchującego
            socket.close();
            listenThread.interrupt();
            listenThread.join();
        }
        catch(InterruptedException e){
            System.err.println(e.getMessage());
        }
    }


    void start() {
        System.out.println("Oczekiwanie na klientów...");

        //stworzenie wątku nasłuchującego
        listenThread = new Thread(new Listener(socket, this));

        //i uruchomienie go
        listenThread.start();

        //oczekiwanie na podłączenie dwóch klientów
        while (klienci.size() < 2) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                System.err.println(e.getMessage());
            }
        }

        //przygotowanie elementów rozgrywki
        System.out.println("Przygotowywanie gry");
        maxczas();
        losujliczbe();

        //utuchomienie pętli gry
        runGaame();
    }

}