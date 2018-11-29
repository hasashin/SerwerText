public class TestMain {

    public static void main(String[] args) {

        //sprawdzenie ilości argumentów
        if (args.length == 1) {

            //uruchomienie serwera
            Serwer serwer = new Serwer(Integer.parseInt(args[0]));
            serwer.start();
        }
        System.out.println("Kończenie pracy");
    }

}
