package raytracer.cameras;

import raytracer.basic.ColorEx;
import raytracer.basic.RaytracerConstants;
import raytracer.util.FloatingPoint;

import java.util.Arrays;

class AsyncCameraThread extends Thread
{
    /** Minimale zeitliche Verz�gerung zwischen zwei <code>renderUpdate</code>-
     * Events (in Millisekunden).*/
    protected final static int UPDATE_DELAY = 50; 
    
    
    /** Referenz auf die Kamera. */
    protected AsyncCamera camera = null;
    /** Zeitpunkt des letzten gerenderten Strahls. */
    protected long renderTime = 0L;
    /** Fortschritt des aktuellen Rendering-Vorgangs.*/
    protected long currentProgress = -1L, expectedProgress = -1L;
    /** Gibt an, ob das Antialiasing schon gestartet wurde. */
    protected boolean antialiasing = false;
    
    
    AsyncCameraThread(AsyncCamera camera)
    {
        super();
        this.camera = camera;
        
    }
    
    
    /**
     * Dieser Thread rendert die Szene und benachrichtigt dabei alle
     * <code>RendererListener</code>.
     */
    @Override
    public void run()
    {
        try
        {

            
            int lowestResolution = camera.sizeExponent;

            
            expectedProgress = (long)camera.resX* (long) camera.resY * (long) (2 + RaytracerConstants.RAYS_PER_PIXEL);
            /*expectedProgress = (1L << (camera.resolution*2))*camera.resX*camera.resY + 
            (1L << camera.resolution)*(camera.resX+camera.resY) + 1;*/
            currentProgress = 0L;
            
            
            updateImage(0.0, 0.0, camera.resX, camera.resY);
            updateImage((double) camera.resX, 0.0, camera.resX, camera.resY);
            updateImage(0.0, (double) camera.resY, camera.resX, camera.resY);
            updateImage((double) camera.resX, (double) camera.resY, camera.resX, camera.resY);
            
            
            
            double offset = 1 << camera.sizeExponent;

            
            int countX;
            int y;
            int x;
            int a;
            for (a = 0; a < lowestResolution; a++) {
                double factor = offset;

                
                countX = (int)Math.ceil((double) camera.resX /factor);
                int countY = (int) Math.ceil(camera.resY / factor);

                
                offset /= 2.0;
                int pixelSize = (int) offset;

                
                for (y = 0; y < countY; y++)
                    for (x = 0; x < countX; x++)
                        updateImage(offset+factor* (double) x, offset+factor* (double) y, pixelSize, pixelSize);
                
                
                for (y = 0; y < countY; y++)
                    for (x = 0; x < countX; x++)
                    {
                        updateImage(factor* (double) x, offset+factor* (double) y, pixelSize, pixelSize);
                        updateImage(offset+factor* (double) x, factor* (double) y, pixelSize, pixelSize);
                    }
                for (x = 0; x < countX; x++)
                    updateImage(offset+factor* (double) x, (double) camera.resY, pixelSize, pixelSize);
                for (y = 0; y < countY; y++)
                    updateImage((double) camera.resX, offset+factor* (double) y, pixelSize, pixelSize);

                ensureUpdated();
            }
            
            
            antialiasing = true;
            for (a = 1; a < RaytracerConstants.RAYS_PER_PIXEL; a++)
                for (y = 0; y < camera.resY; y++)
                    for (x = 0; x < camera.resX; x++)
                    {
                        
                        updateImage((double) x + FloatingPoint.nextRandom(),
                                (double) y + FloatingPoint.nextRandom(), 1, 1);
                    }
            
            
            
            
            countX = camera.resX*camera.resY;
            
            ColorEx pixel = new ColorEx();
            for (x = 0; x < countX; x++) {
                
                pixel.x = camera.rmap[x];
                pixel.y = camera.gmap[x];
                pixel.z = camera.bmap[x];
                pixel.scale(1.0f / camera.countmap[x]);
                camera.bitmap[x] = pixel.get().getRGB();
            }
            
            
            camera.state.set(AsyncCamera.STATE_RENDERED);
            
            
            camera.fireRenderFinished();
        }
        catch (NullPointerException e)
        {
            
            if (camera != null)
                throw e;
        }
    }
    
    
    /**
     * Aktualisiert das Bild, was gerade generiert wird.<br>
     * Dabei wird ein Strahl in die Szene geschickt und der dadurch erhaltene
     * Farbwert der Bitmap zugewiesen. Liegt ein Strahl genau an der Ecke
     * von benachbarten Pixeln, so wird der Farbwert jedem benachbarten Pixel
     * zugewiesen.<br>
     * Diese Methode unterst�tzt die grobk�rnige Darstellung. In der Bitmap wird
     * ein Pixelblock der Breite <code>pixelWidth</code> und der H�he
     * <code>pixelHeight</code>, mit dem zugeh�rigen Pixel im Zentrum,
     * eingef�rbt.
     *  @param x x-Koordinate des zu sendenen Strahls.
     * @param y x-Koordinate des zu sendenen Strahls.
     * @param pixelWidth H�he des zu f�rbenden Pixelblocks.
     * @param pixelHeight Breite des zu f�rbenden Pixelblocks.
     */
    private void updateImage(final double x, final double y,
                             final int pixelWidth, final int pixelHeight)
    {
        
        if ((x > (double) camera.resX) || (y > (double) camera.resY))
            return;

        int i = (int)x, j = (int)y;
        
        
        
        
        if ((!antialiasing) || (camera.aaUnchangedCount[j*camera.resX+i] <
                RaytracerConstants.AA_MAX_UNCHANGED_COUNT))
        {
            
            ColorEx color = camera.getColor(x, y);
            color.clampMin(0.0f);
            color.clampMax(1.0f);
            
            
            if (i == camera.resX)
                i--;
            if (y == camera.resY)
                j--;
            
            
            
            boolean updateX1 = (i == x) && (i != 0);
            boolean updateX2 = (j == y) && (j != 0);
            if (updateX1)
                updatePixel(i-1, j, color, 1, 1);
            if (updateX2)
                updatePixel(i, j-1, color, 1, 1);
            if ((updateX1) && (updateX2))
                updatePixel(i-1, j-1, color, 1, 1);
            
            
            updatePixel(i, j, color, pixelWidth, pixelHeight);
        }
        
        
        if (currentProgress < expectedProgress)
            currentProgress++;

        ensureUpdated();
    }

    protected void ensureUpdated() {
        


            long time = System.currentTimeMillis();
            if (time >= renderTime+ (long) UPDATE_DELAY)
            {
                renderTime = time;
                camera.fireRenderUpdate((double)currentProgress/ (double) expectedProgress);
            }

    }
    
    /**
     * Aktualisiert ein Pixel im Bild und erzeugt dabei ein Vorschaubild, in
     * dem das Pixel und ein umliegender Pixelblock eingef�rbt werden.
     * 
     * @param x x-Koordiante des zu aktualisierenden Pixels.
     * @param y y-Koordiante des zu aktualisierenden Pixels.
     * @param color Farbe f�r das Pixel.
     * @param pixelWidth Breite des zu f�rbenden Pixelblocks im Vorschaubild.
     * @param pixelHeight H�he des zu f�rbenden Pixelblocks im Vorschaubild.
     */
    private void updatePixel(int x, int y, ColorEx color,
            int pixelWidth, int pixelHeight)
    {
        final int resX = camera.resX;
        final int resY = camera.resY;

        int index = y*resX+x;
        int count = camera.countmap[index];
        
        if (antialiasing) {
            
            double difference = camera.pixelDifference(color, index, count);
            if (difference < RaytracerConstants.AA_MIN_DIFFERENCE)
                camera.aaUnchangedCount[index]++;
            else
                camera.aaUnchangedCount[index] = 0;
        }
        
        
        camera.absorb(index, color);

        
        ColorEx pixel = new ColorEx(camera.rmap[index], camera.gmap[index], camera.bmap[index]);
        pixel.scale(1.0f/camera.countmap[index]);
        int rgb = pixel.get().getRGB();
        
        
        int startX = x-pixelWidth/2;
        int startY = y-pixelHeight/2;
        int endX = x+pixelWidth/2;
        int endY = y+pixelHeight/2;
        
        
        if (startX < 0) startX = 0;
        if (startY < 0) startY = 0;
        if (endX >= resX) endX = resX-1;
        if (endY >= resY) endY = resY-1;

        
        for (y = startY; y <= endY; y++) {
            final int o = y * resX;
            Arrays.fill(camera.bitmap, o + startX, o + endX + 1, rgb);



        }
    }




    /**
     * Signalisiert dem Thread, dass es Zeit ist, zu sterben.
     */
    public void die()    {
        camera = null;
    }
}