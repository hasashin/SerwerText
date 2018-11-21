import java.io.IOException;
import java.net.*;
import java.util.*;


class Serwer {

    class Pair {
        public final Integer key;
        public final DatagramPacket value;

        Pair(Integer x, DatagramPacket y){
            this.key = x;
            this.value = y;
        }
    }

    private int czasrozgrywki;
    private long poczatkowy;
    private int liczba;
    private int numerklienta;
    private boolean warunek = true;
    private DatagramSocket socket;
    Vector<Pair> klienci = new Vector<>();
    Thread l1;


    Serwer(int port) {
        try {
            socket = new DatagramSocket(port);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    DatagramPacket getPacketById(int id){
        for(Pair elem : klienci){
            if(id == elem.key){
                return elem.value;
            }
        }
        return null;
    }

    private int generuj() {
        Random generator = new Random();
        numerklienta = (generator.nextInt(30) + 1);

        for(Pair elem : klienci){
            if(numerklienta == elem.key){
                return 0;
            }
        }
        return numerklienta;
    }

    private void maxczas() {

        int id1 = klienci.get(0).key;
        int id2 = klienci.get(1).key;

        czasrozgrywki = (Math.abs(id1 - id2) * 99) % 100 + 30;
    }

    private void losujliczbe() {
        Random generator = new Random();
        liczba = (generator.nextInt(254)+1);
        System.out.println("Wybrano " + liczba);
    }

    void broadcast(String operacja, String odpowiedz, int liczba, int czas){
        for(Pair elem : klienci){
            wyslijpakiet(operacja, odpowiedz, liczba, czas, elem.value.getAddress(), elem.value.getPort());
        }
    }

    void sprawdz(int odp, DatagramPacket dat) {
        if (odp == liczba) {
            wyslijpakiet("end", "wygrana", liczba, 0, dat.getAddress(), dat.getPort());

            for(Pair elem : klienci){
                if(elem.value != dat){
                    wyslijpakiet("end", "przegrana", liczba, 0, dat.getAddress(), dat.getPort());
                }
            }
        } else {
            if(odp < liczba)
                wyslijpakiet("nofity", "duza", 0, 0, dat.getAddress(), dat.getPort());
            else
                wyslijpakiet("notify", "mala", 0, 0, dat.getAddress(), dat.getPort());
        }
    }

    private void ileczasu() {
        long obecny = System.currentTimeMillis() / 1000;
        long uplynelo = obecny - poczatkowy;
        int zostalo = (int)(czasrozgrywki - uplynelo);
        if (zostalo > 0) {
            System.out.println("Zostalo " + zostalo + " sekund");
            broadcast("notify", "czas", 0, zostalo);
        } else {
            broadcast("end", "koniecCzasu", 0, 0);
            warunek = false;
        }

    }

    private DatagramPacket generujPakiet(String operacja, String odpowiedz, int id, int liczba, InetAddress ip, int port) {

        byte[] buff = new byte[256];

        DatagramPacket pakiet = new DatagramPacket(buff,256);

        String komunikat = "";

        komunikat += "OP?"+operacja+"<<";
        komunikat += "OD?"+odpowiedz+"<<";
        komunikat += "ID?"+id+"<<";
        komunikat += "LI?"+liczba+"<<";
        komunikat += "CZ?"+0+"<<";

        pakiet.setData(komunikat.getBytes());

        pakiet.setAddress(ip);
        pakiet.setPort(port);

        return pakiet;
    }

    void wyslijpakiet(String operacja, String odpowiedz, int liczba, int czas, InetAddress ip, int port) {
        try {
            socket.send(generujPakiet(operacja, odpowiedz, liczba, czas, ip, port));
        } catch (IOException r) {
            System.err.println(r.getMessage());
        }
    }

     void decode(DatagramPacket pakiet) {
        int liczba,id;
        String[] options = new String(pakiet.getData()).split("<<");

        Hashtable<String,String> optionsSplit = new Hashtable<>();

        for(String elem : options){
            String[] temp = elem.split("[?]");
            if(temp.length == 2)
                optionsSplit.put(temp[0],temp[1]);
        }

        liczba = Integer.parseInt(optionsSplit.get("LI"));
        id = Integer.parseInt(optionsSplit.get("ID"));

        Boolean warunek = false;
        for(Pair elem : klienci){
            if(elem.key == id){
                warunek = true;
                break;
            }
        }

        if (warunek || id == 0) {
            execute(optionsSplit.get("OP"), optionsSplit.get("OD"),liczba,id, pakiet);
        } else {
            System.out.println("Odebrano niepoprawny komunikat od serwera");
        }

    }

    private void execute(String operacja, String odpowiedz, int liczba, int id, DatagramPacket pakiet) {
        if (operacja.equals("notify") && odpowiedz.equals("liczba")) {
            sprawdz(liczba, pakiet);
        }
        if (operacja.equals("end") && odpowiedz.equals("zakonczPol")) {
            System.out.println("Klient " + id + " kończy połączenie");
            zakoncz(id);
        }
        if (operacja.equals("connect") && odpowiedz.equals("chce")){
            System.out.println("Klient " + id + " połączył się");
            wyslijpakiet("response","ACK", id,0, pakiet.getAddress(), pakiet.getPort());
            klienci.add(new Pair(id, pakiet));
        }
        if(operacja.equals("response") && odpowiedz.equals("ACK")){
            return;
        }

        wyslijpakiet("response","ACK", id,0, pakiet.getAddress(), pakiet.getPort());
    }

    private void zakoncz(int id) {
        for(Pair elem : klienci){
            if(elem.key == id){
                klienci.remove(elem);
            }
        }
    }

    void runGaame(){
        long dziesiec = System.currentTimeMillis() / 1000;
        poczatkowy = System.currentTimeMillis() / 1000;
        System.out.println("Start");

        while (warunek) {
            if ((System.currentTimeMillis() / 1000 - dziesiec) > 9) {
                ileczasu();
                dziesiec = System.currentTimeMillis() / 1000;
            }
            if ((poczatkowy * czasrozgrywki) - System.currentTimeMillis() / 1000 <= 0) {
                ileczasu();
            }
            if (klienci.size() <= 0) {
                warunek = false;
            }
        }

        l1.interrupt();
    }


    void start() {
        generuj();
        System.out.println("Oczekiwanie na klientów...");

        l1 = new Thread(new Klient(socket, this));

        l1.start();

        while(klienci.size() < 2){
            try{ Thread.sleep(100);}
            catch (InterruptedException e) {System.err.println(e.getMessage());}
        }

        System.out.println("Przygotowywanie gry");
        maxczas();
        losujliczbe();

        runGaame();
    }

}