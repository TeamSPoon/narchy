package spacegraph.video;

import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.image.GrayU8;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.TextureIO;
import jcog.tree.rtree.rect.RectFloat2D;
import spacegraph.space2d.Surface;
import spacegraph.space2d.SurfaceRender;
import spacegraph.space2d.container.AspectAlign;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.jogamp.opengl.GL.*;
import static com.jogamp.opengl.GL2GL3.GL_UNSIGNED_INT_8_8_8_8_REV;

/** https://www.khronos.org/opengl/wiki/Image_Format */
public class Tex {

    public com.jogamp.opengl.util.texture.Texture texture;

    public boolean mipmap;

    


    final AtomicBoolean textureUpdated = new AtomicBoolean(false);
    public GLProfile profile;
    private TextureData nextData;

    /** weird rotation correction.. dunno why yet */
    boolean inverted = false;

    private Object src;

    public final void paint(GL2 gl, RectFloat2D bounds) {
        paint(gl, bounds, 1f);
    }

    public final void paint(GL2 gl, RectFloat2D bounds, float alpha) {
        paint(gl, bounds, -1, alpha);
    }

    public void paint(GL2 gl, RectFloat2D bounds, float repeatScale, float alpha) {



        if (profile == null) {
            profile = gl.getGLProfile();
        }

        if (nextData != null && textureUpdated.compareAndSet(true, false)) {

            if (texture == null) {
                texture = TextureIO.newTexture(gl, nextData);
            } else if (nextData != null) {
                
                texture.updateImage(gl, nextData);
            }


        }

        if (texture != null) {
            Draw.rectTex(gl, texture, bounds.x, bounds.y, bounds.w, bounds.h, 0, repeatScale, alpha, inverted);
        }

    }

    public void update(BufferedImage iimage) {
        if (iimage == null || profile == null)
            return;

        if (nextData == null || this.src != iimage) {
            DataBuffer b = iimage.getRaster().getDataBuffer();
            int W = iimage.getWidth();
            int H = iimage.getHeight();
            if (b instanceof DataBufferInt)
                update(((DataBufferInt) b).getData(), W, H);
            else if (b instanceof DataBufferByte) {
                update(((DataBufferByte) b).getData(), W, H);
            }
        }

        textureUpdated.set(true);
    }

    protected void update(byte[] iimage, int width, int height) {

        this.src = iimage;

        ByteBuffer buffer = ByteBuffer.wrap(iimage);
        nextData = new TextureData(profile, GL_RGB,
                width, height,
                0 /* border */,
                GL_RGB,
                GL_UNSIGNED_BYTE,
                mipmap,
                false,
                false,
                buffer, null
        );
    }

    protected void update(int[] iimage, int width, int height) {

        this.src = iimage;

        IntBuffer buffer = IntBuffer.wrap(iimage);
        nextData = new TextureData(profile, GL_RGB,
                width, height,
                0 /* border */,
                GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV,
                mipmap,
                false,
                false,
                buffer, null
        );
    }

    public Surface view() {
        return new TexSurface(this);
    }

    public Surface view(float aspect) {
        return new AspectAlign(view(), aspect);
    }

    /** less efficient than: b = update(x, b) */
    public BufferedImage update(GrayU8 x) {
        return update(x, null);
    }

    public BufferedImage update(GrayU8 x, BufferedImage b) {
        this.src = x;

        if (b == null || b.getWidth()!=x.width || b.getHeight()!=x.height)
            b = new BufferedImage(x.width, x.height, BufferedImage.TYPE_INT_ARGB);

        
        update(
            ConvertBufferedImage.convertTo(x, b)
        );

        return b;













    }

    static class TexSurface extends Surface {

        private final Tex tex;

        public TexSurface(Tex tex) {
           this.tex = tex;
        }
        @Override
        protected void paint(GL2 gl, SurfaceRender surfaceRender) {
            try {
                tex.paint(gl, bounds);
            } catch (NullPointerException e) {





            }
        }
    }
}
