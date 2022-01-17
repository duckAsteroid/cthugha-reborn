public class TransformTable {
  private final int[] table;

  public TransformTable(int[] table) {
    this.table = table;
  }

  public void transform(byte[] source, byte[] destination) {
    for(int i =0 ; i < table.length; i++) {
      destination[i] = source[table[i]];
    }
  }
}
