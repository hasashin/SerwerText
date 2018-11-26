import java.io.IOException;
import java.net.*;
import java.sql.Timestamp;
import java.util.*;


class Serwer {

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
            socket = new DatagramSocket(port);
            listener = new Listener(socket,this);
            timestamp = new Timestamp(System.currentTimeMillis());
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private boolean isClientConnected(String address){
        String[] inetData = address.split(":");
        boolean ret;
        try{
            DatagramSocket soc = new DatagramSocket(Integer.parseInt(inetData[1]),InetAddress.getByName(inetData[0]));
            ret = false;
            soc.close();
        }
        catch(IOException e){
            ret = true;
        }
        return ret;
    }

    private int getIdByPacket(DatagramPacket dat) {
        for (Pair elem : klienci) {
            if(elem.value.equals(dat.getAddress().getHostAddress()+":"+dat.getPort())){
                return elem.key;
            }
        }
        return 0;
    }
    private int getIdByPacket(String inetData){
        for (Pair elem : klienci){
            if(elem.value.equals(inetData)){
                return elem.key;
            }
        }
        return 0;
    }

    private String getStringFromID(int id){
        for(Pair elem : klienci){
            if(elem.key == id){
                return elem.value;
            }
        }
        return null;
    }

    private int generuj() {
        int numerklienta;
        Random generator = new Random();
        numerklienta = (generator.nextInt(30) + 1);

        for (Pair elem : klienci) {
            if (numerklienta == elem.key) {
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
        liczba = (generator.nextInt(254) + 1);
        System.out.println("Wybrano " + liczba);
    }

    private void broadcast(String operacja, String odpowiedz, int czas) {
        Pair[] arr = new Pair[klienci.size()];
        klienci.toArray(arr);
        for (Pair elem : arr) {
            String[] addr = elem.value.split(":");
            try {
                wyslijpakiet(operacja, odpowiedz, elem.key, 0, czas, InetAddress.getByName(addr[0]), Integer.parseInt(addr[1]));
            }catch (IOException e){
                System.err.println(e.getMessage()+"e");
            }
        }
    }

    private void sprawdz(int odp, DatagramPacket dat) {
        if (odp == liczba) {
            ingame = false;
            wyslijpakiet("end", "wygrana", getIdByPacket(dat), liczba, 0, dat.getAddress(), dat.getPort());

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
            if (odp > liczba)
                wyslijpakiet("notify", "duza", getIdByPacket(dat), 0, 0, dat.getAddress(), dat.getPort());
            else
                wyslijpakiet("notify", "mala", getIdByPacket(dat), 0, 0, dat.getAddress(), dat.getPort());
        }
    }

    private void ileczasu() {
        long obecny = System.currentTimeMillis() / 1000;
        long uplynelo = obecny - poczatkowy;
        int zostalo = (int) (czasrozgrywki - uplynelo);
        if (zostalo > 0) {
            System.out.println("Zostalo " + zostalo + " sekund");
            broadcast("notify", "czas", zostalo);
        } else {
            broadcast("end", "koniecCzasu", 0);
        }

    }

    private DatagramPacket generujPakiet(String operacja, String odpowiedz, int id, int liczba, int czas, InetAddress ip, int port) {
        byte[] buff = new byte[256];

        DatagramPacket pakiet = new DatagramPacket(buff, 256);

        String komunikat = "";

        komunikat += "OP?" + operacja + "<<";
        komunikat += "OD?" + odpowiedz + "<<";
        komunikat += "ID?" + id + "<<";
        komunikat += "LI?" + liczba + "<<";
        komunikat += "CZ?" + czas + "<<";
        komunikat += "TS?" + timestamp.getTime() +"<<";
        komunikat += "\0";

        pakiet.setData(komunikat.getBytes());

        pakiet.setAddress(ip);
        pakiet.setPort(port);

        return pakiet;
    }

    private void wyslijpakiet(String operacja, String odpowiedz, int id, int liczba, int czas, InetAddress ip, int port) {
        try {
            if(isClientConnected(ip.getHostAddress()+":"+port))
                socket.send(generujPakiet(operacja, odpowiedz, id, liczba, czas, ip, port));
            else {
                if(ingame) {
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
        String[] options = new String(pakiet.getData()).split("<<");

        Hashtable<String, String> optionsSplit = new Hashtable<>();

        for (String elem : options) {
            String[] temp = elem.split("[?]");
            if (temp.length == 2)
                optionsSplit.put(temp[0], temp[1]);
        }

        liczba = Integer.parseInt(optionsSplit.get("LI"));
        id = Integer.parseInt(optionsSplit.get("ID"));

        boolean exists = false;
        for (Pair elem : klienci) {
            if (elem.key == id) {
                exists = true;
                break;
            }
        }

        if (exists || id == 0) {
            execute(optionsSplit.get("OP"), optionsSplit.get("OD"), liczba, id, pakiet);
        } else {
            System.out.println("Odebrano niepoprawny komunikat od klienta "+id);
        }

    }

    private void execute(String operacja, String odpowiedz, int liczba, int id, DatagramPacket pakiet) {
        if (!operacja.equals("response") && !odpowiedz.equals("ACK")) {
            wyslijpakiet("response", "ACK", id, 0, 0, pakiet.getAddress(), pakiet.getPort());
            try{
                Thread.sleep(100);
            }catch(InterruptedException e){
                System.err.println(e.getMessage());
            }
        }

        if (operacja.equals("notify") && odpowiedz.equals("liczba")) {
            sprawdz(liczba, pakiet);
        }
        if (operacja.equals("end") && odpowiedz.equals("zakonczPol")) {
            System.out.println("Klient " + id + " kończy połączenie");
            while(del){
                try{
                    Thread.sleep(10);
                }catch(InterruptedException e){
                    System.err.println(e.getMessage());
                }
            }
            delID = id;
            del = true;
        }
        if (operacja.equals("connect") && odpowiedz.equals("chce")) {
            id = generuj();
            System.out.println("Klient " + id + " połączył się");
            wyslijpakiet("answer", "accept", id, 0, 0, pakiet.getAddress(), pakiet.getPort());
            DatagramPacket pak = new DatagramPacket(new byte[256], 256, pakiet.getAddress(), pakiet.getPort());
            klienci.add(new Pair(id, pak.getAddress().getHostAddress()+":"+pak.getPort()));

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
        Pair torem = null;
        Pair[] arr = new Pair[klienci.size()];
        klienci.toArray(arr);
        for (Pair elem : arr) {
            if (elem.key == id) {
                torem = elem;
            }
        }
        if (torem != null) {
            klienci.remove(torem);
            if(klienci.size() == 0)
                klienci = null;
        }
        del = false;
        delID = 0;
    }

    private void runGaame() {
        long dziesiec = System.currentTimeMillis() / 1000;
        poczatkowy = System.currentTimeMillis() / 1000;
        System.out.println("Start");
        boolean warunek = true;

        while (warunek) {
            if ((System.currentTimeMillis() / 1000 - dziesiec) >= 10) {
                ileczasu();
                dziesiec = System.currentTimeMillis() / 1000;
            }
            if ((poczatkowy * czasrozgrywki) - System.currentTimeMillis() / 1000 <= 0) {
                ileczasu();
            }
            if (del) {
                zakoncz(delID);
            }
            if (klienci == null) {
                listener.warunek = false;
                warunek = false;
            }
        }

        try {
            socket.close();
            listenThread.interrupt();
            listenThread.join();
        }
        catch(InterruptedException e){
            System.err.println(e.getMessage());
        }
    }


    void start() {
        generuj();
        System.out.println("Oczekiwanie na klientów...");

        listenThread = new Thread(new Listener(socket, this));

        listenThread.start();

        while (klienci.size() < 2) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                System.err.println(e.getMessage());
            }
        }

        System.out.println("Przygotowywanie gry");
        maxczas();
        losujliczbe();

        runGaame();
    }

}