/**
 * Project : JHelpSceneGraph<br>
 * Package : jhelp.engine.anim<br>
 * Class : AnimationKeyFrame<br>
 * Date : 4 sept. 2008<br>
 * By JHelp
 */
package jhelp.engine.anim;

import com.jogamp.opengl.GL;
import jhelp.engine.Animation;
import jhelp.util.list.ArrayInt;

import java.util.ArrayList;

/**
 * Generic animation by key frames<br>
 * Use when animation is compose on (key,value) pair.<br>
 * This class says at that frame the object state must be that<br>
 * <br>
 * <br>
 * Last modification : 20 janv. 2009<br>
 * Version 0.0.1<br>
 * 
 * @author JHelp
 * @param <ObjectType>
 *           Type of the modified object
 * @param <Value>
 *           Type of the value change by the animation
 */
public abstract class AnimationKeyFrame<ObjectType, Value>
      implements Animation
{
   /** Keys list */
   private final ArrayInt         keys;
   /** Object modified */
   private final ObjectType       object;
   /** Absolute start frame */
   private float                  startAbsoluteFrame;
   /** Start vale */
   private Value                  startValue;
   /** Values list */
   private final ArrayList<Value> values;

   /**
    * Constructs AnimationKeyFrame
    * 
    * @param object
    *           Object modified
    */
   public AnimationKeyFrame(final ObjectType object)
   {
      if(object == null)
      {
         throw new NullPointerException("The object musn't be null !");
      }

      this.keys = new ArrayInt();
      this.object = object;
      this.values = new ArrayList<Value>();
   }

   /**
    * Interpolate a value and change the object state
    * 
    * @param object
    *           Object to change
    * @param before
    *           Value just before the wanted state
    * @param after
    *           Value just after the wanted state
    * @param percent
    *           Percent of interpolation
    */
   protected abstract void interpolateValue(ObjectType object, Value before, Value after, float percent);

   /**
    * Give the actual value for an object
    * 
    * @param object
    *           Object we want extract the value
    * @return The actual value
    */
   protected abstract Value obtainValue(ObjectType object);

   /**
    * Change object state
    * 
    * @param object
    *           Object to change
    * @param value
    *           New state value
    */
   protected abstract void setValue(ObjectType object, Value value);

   /**
    * Add a frame
    * 
    * @param key
    *           Frame key
    * @param value
    *           Value at the frame
    */
   public final void addFrame(final int key, final Value value)
   {
      int index;
      int size;

      if(value == null)
      {
         throw new NullPointerException("The value musn't be null !");
      }

      if(key < 0)
      {
         throw new IllegalArgumentException("The key must be >=0 not " + key);
      }

      
      index = this.keys.getIndex(key);
      if(index >= 0)
      {
         this.values.set(index, value);
         return;
      }

      
      size = this.keys.getSize();
      for(index = 0; (index < size) && (this.keys.getInteger(index) < key); index++)
      {
         ;
      }

      
      if(index < size)
      {
         this.keys.insert(key, index);
         this.values.add(index, value);
         return;
      }

      
      this.keys.add(key);
      this.values.add(value);
   }

   /**
    * Play the animation
    * 
    * @param gl
    *           OpenGL context
    * @param absoluteFrame
    *           Actual absolute frame
    * @return {@code true} if the animation is not finish
    * @see jhelp.engine.Animation#animate(javax.media.opengl.GL, float)
    */
   @Override
   public final boolean animate(final GL gl, final float absoluteFrame)
   {
      int firstFrame;
      int lastFrame;
      int frame;
      int size;
      float actualFrame;
      float percent;
      Value before;
      Value after;

      
      size = this.keys.getSize();
      if(size < 1)
      {
         return false;
      }

      
      firstFrame = this.keys.getInteger(0);
      lastFrame = this.keys.getInteger(size - 1);
      actualFrame = absoluteFrame - this.startAbsoluteFrame;

      
      
      
      if(actualFrame < firstFrame)
      {
         
         if(this.startValue == null)
         {
            this.startValue = this.obtainValue(this.object);
         }

         before = this.startValue;
         after = this.values.get(0);
         percent = actualFrame / firstFrame;

         this.interpolateValue(this.object, before, after, percent);

         return true;
      }

      this.startValue = null;

      
      
      if(actualFrame >= lastFrame)
      {
         this.setValue(this.object, this.values.get(size - 1));
         return false;
      }

      
      for(frame = 0; (frame < size) && (this.keys.getInteger(frame) < actualFrame); frame++)
      {
         ;
      }

      
      
      if(frame == 0)
      {
         this.setValue(this.object, this.values.get(0));
         return true;
      }

      
      
      if(frame >= size)
      {
         this.setValue(this.object, this.values.get(size - 1));
         return false;
      }

      
      before = this.values.get(frame - 1);
      after = this.values.get(frame);
      percent = (actualFrame - this.keys.getInteger(frame - 1)) / (this.keys.getInteger(frame) - this.keys.getInteger(frame - 1));

      this.interpolateValue(this.object, before, after, percent);

      return true;
   }

   /**
    * Define the start absolute frame
    * 
    * @param startAbsoluteFrame
    *           New start absolute frame
    * @see jhelp.engine.Animation#setStartAbsoluteFrame(float)
    */
   @Override
   public final void setStartAbsoluteFrame(final float startAbsoluteFrame)
   {
      this.startAbsoluteFrame = startAbsoluteFrame;
      this.startValue = null;
   }
}