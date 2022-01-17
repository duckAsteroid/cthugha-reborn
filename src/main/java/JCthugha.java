import java.awt.*;
import java.applet.*;
import java.awt.image.*;

public class JCthugha extends Panel implements Runnable, ImageObserver {

	int [] sound;

	byte[] pixels;
	int width, height;
	Image img;
	MemoryImageSource source;

	Thread thread;

	public void init() {
		IndexColorModel icm;

		width = getSize().width;
		height = getSize().height;

		sound = new int[width];

		byte[] reds = new byte[256];
		byte[] greens = new byte[256];
		byte[] blues = new byte[256];

		for( int i=0; i < 64; i++ ) {
			reds[i] = (byte) (i*4);
			greens[i+64] = (byte) (i*4);
			blues[i+128] = (byte) (i*4);

			reds[i+192] = (byte) (i*4);
			greens[i+192] = (byte) (i*4);
			blues[i+192] = (byte) (i*4);
		}

		pixels = new byte[ getSize().width * getSize().height ];
		icm = new IndexColorModel(8, 256, reds, greens, blues);
		source = new MemoryImageSource( getSize().width, getSize().height, icm, pixels, 0, getSize().width);
		source.setAnimated(true);
		img = createImage( source );
		prepareImage( img, this );

		thread = new Thread(this);
	}

	public boolean imageUpdate(Image img, int flags, int x, int y, int w, int h) {
		if( ( flags & (ImageObserver.FRAMEBITS | ImageObserver.ALLBITS) ) != 0 ) {
			repaint();
		}
		if( ( flags & (ImageObserver.ERROR | ImageObserver.ABORT) ) != 0 ) {
			return false;
		}
		return true;
	}

	public void paint(Graphics g) {
		update(g);
	}

	public void update(Graphics g) {
		g.drawImage(img, 0, 0, this);
	}

	public void start() { thread.start(); }
	public void stop() { thread.stop(); }

	public void run() {
		Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
		while(true) {

			// create new sound
			sound[0] = rand(-height/3,height/3);
			for(int i=1; i < width; i++) {
				sound[i] = sound[i-1] + rand(-10,10);
				if(sound[i] < -height) sound[i] += 2*height;
				if(sound[i] > height)  sound[i] -= 2*height;
			}

			int ib = (height-1)*width; // bottom row start index
			// flame
			for(int i=0; i < width; i++) {
				pixels[i] = 0;
				pixels[ib + i] = 0;
			}
			ib = 0;
			for(int i=0; i < height; i++) {
				pixels[ ib ] = 0;
				pixels[ ib + width - 1 ] = 0;
				ib += width;
			}

			for( int i=1; i < (height-1)*(width-1); i++ ) {
				int p1 = pixels[ i+1 ];
				int p2 = pixels[ i+width ];
				int p3 = pixels[ i+width + 1];
				int p4 = pixels[ i ];

				if( p1 < 0 ) p1 += 256;
				if( p2 < 0 ) p2 += 256;
				if( p3 < 0 ) p3 += 256;
				if( p4 < 0 ) p4 += 256;

				int s = p1 + p2 + p3 + p4;

				s = s / 4;

				if(s > 0) s--;
				pixels[ i ] = (byte)s;
			}

			int ib1 = height / 2;

			// wave
			for(int i=0; i < width; i++) {
				pixels[ (ib1 + sound[i] / 3) * width + i] = (byte) 255;
			}

			source.newPixels();
		}
	}

	static int rand(int low, int high) {
		return (int)( low + Math.random()*(high-low));
	}
}
