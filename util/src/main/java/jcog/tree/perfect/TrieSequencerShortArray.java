/* 
 * NOTICE OF LICENSE
 * 
 * This source file is subject to the Open Software License (OSL 3.0) that is 
 * bundled with this package in the file LICENSE.txt. It is also available 
 * through the world-wide-web at http:
 * If you did not receive a copy of the license and are unable to obtain it 
 * through the world-wide-web, please send an email to magnos.software@gmail.com 
 * so we can send you a copy immediately. If you use any of this software please
 * notify me via our website or email, your feedback is much appreciated. 
 * 
 * @copyright   Copyright (c) 2011 Magnos Software (http:
 * @license     http:
 *              Open Software License (OSL 3.0)
 */

package jcog.tree.perfect;




/**
 * A {@link TrieSequencer} implementation where short[] is the sequence type.
 * 
 * @author Philip Diffenderfer
 * 
 */
public class TrieSequencerShortArray implements TrieSequencer<short[]>
{

   @Override
   public int matches( short[] sequenceA, int indexA, short[] sequenceB, int indexB, int count )
   {
      for (int i = 0; i < count; i++)
      {
         if (sequenceA[indexA + i] != sequenceB[indexB + i])
         {
            return i;
         }
      }

      return count;
   }

   @Override
   public int lengthOf( short[] sequence )
   {
      return sequence.length;
   }

   @Override
   public int hashOf( short[] sequence, int i )
   {
      return sequence[i];
   }

}
