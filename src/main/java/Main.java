import java.awt.Frame;

public class Main {
  public static void main(String[] args) {
      Frame f = new Frame();
      JCthugha jCthugha = new JCthugha();
      jCthugha.setBounds(0, 0, 1024, 1024);
      f.add(jCthugha);         //adding a new Button.
      f.setSize(1024, 1024);        //setting size.
      f.setTitle("Java Cthugha");  //setting title.
      //f.setLayout(null);   //set default layout for frame.
      f.setVisible(true);           //set frame visibility true
      jCthugha.init();
      jCthugha.start();
  }
}
