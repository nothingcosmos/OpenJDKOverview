import java.lang.*;

public class Fact{
    public static void main (String[] args) {
      for (int i = 0; i < 100000; i++) {
        if (factIf(i%1000) == factFor(i%1000)) {
        } else {
          System.out.println("not eq\n");
          break;
        }
      }
      System.out.println("OK");
    }
    public static int factIf(int n) {
      int p;
      if (n > 1) {
        p = n * factIf(n - 1);
      } else {
        p = 1;
      }
      return p;
    }

    public static int factFor(int n) {
      int p = 1;
      for (int i = 1; i <= n; i++) {
        p = p * i;
      }
      return p;
    }
}
