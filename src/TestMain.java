public class TestMain {

    public static void main(String[] args) {
        if (args.length == 1) {
            Serwer serwer = new Serwer(Integer.parseInt(args[0]));
            serwer.start();
        }
        System.out.println("Kończenie pracy");
    }

}
