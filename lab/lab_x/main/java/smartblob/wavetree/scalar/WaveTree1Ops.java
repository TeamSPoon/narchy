/** Ben F Rayfield offers Wavetree opensource GNU LGPL 2+ */
package smartblob.wavetree.scalar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** static functions relevant to class WaveTree1
*/
public class WaveTree1Ops{

	/** balances x to become an AVL tree */
	public static WaveTree1 balanceAVL(WaveTree1 x){
		return balance(x,(byte)1);
	}

	/** If maxheightDiff is 1, balances x to be an AVL tree. maxheightDiff can not be less than 1.
	Always returns a WaveTree1 whose maxheightDiff is at most the parameter maxheightDiff.
	*/
	public static WaveTree1 balance(WaveTree1 x, byte maxheightDiff){
		if(maxheightDiff < 1) throw new RuntimeException("can not require maxheightDiff be less than 1");
		if(x.maxheightDiff <= maxheightDiff) return x;
		if(maxheightDiff != 1) throw new RuntimeException(
			"maxheightDiff must be 1. TODO: make this algorithm work with higher maxheightDiff.");
		WaveTree1 newLeft = x.left.maxheightDiff<=1 ? x.left : balance(x.left,maxheightDiff);
		WaveTree1 newRight = x.right.maxheightDiff<=1 ? x.right : balance(x.right,maxheightDiff);
		
		
		while(newLeft.height+maxheightDiff < newRight.height){
			
			
			
			if(newRight.left.height > newRight.right.height){
				
				
				
				newRight = new WaveTree1(newRight.left.left, new WaveTree1(newRight.left.right,newRight.right));
			}
			
			newLeft = balance( new WaveTree1(newLeft,newRight.left), maxheightDiff );
			newRight = newRight.right;
		}
		
		while(newLeft.height > newRight.height+maxheightDiff){
			
			
			
			
			if(newLeft.right.height > newLeft.left.height){
				
				
				
				newLeft = new WaveTree1(new WaveTree1(newLeft.left, newLeft.right.left), newLeft.right.right);
			}
			
			newRight = balance( new WaveTree1(newLeft.right, newRight), maxheightDiff );
			newLeft = newLeft.left;
		}
		
		
		
		
		return new WaveTree1(newLeft,newRight);
	}


	public static WaveTree1 concat(WaveTree1 x, WaveTree1 dt){
		return new WaveTree1(x,dt);
	}


	/** Example: "[2.3333333333333335@6.0{1.0@1.0}[2.6@5.0{2.0@2.0}{3.0@3.0}]]" */
	public static WaveTree1 fromString(String waveTreeEncoded){
		
		
		
		
		waveTreeEncoded = waveTreeEncoded.replace('[','{').replace('(','{').replace(']','}').replace(')','}');
		throw new RuntimeException("code not finished");
	}

	public static WaveTree1 intern(WaveTree1 x){
		throw new RuntimeException("TODO: make x function be like String.intern()");
	}

	

	public static double valueAt(WaveTree1 x, double index){
		if(x.isLeaf()){ 
			return x.value1;
		}else{ 
			return index<x.left.len
				? valueAt(x.left,index)
				: valueAt(x.right,index-x.left.len);
		}
	}

	public static WaveTree1 subtree(WaveTree1 x, double getStartIndex, double getEndIndex){
		return subtreeFromIndexAndSize(x, getStartIndex, getEndIndex-getStartIndex);
	}

	public static WaveTree1 leaf(double value, double size){
		return new WaveTree1(value,size);
	}

	
	/** assumes 0 <= startIndex < startIndex+len <= x.size */
	public static WaveTree1 subtreeFromIndexAndSize(WaveTree1 x, double getStartIndex, double getSize){
		if(x.isLeaf()) return new WaveTree1(x.value1,getSize);
		if(getStartIndex < x.left.len){ 
			if(getStartIndex+getSize <= x.left.len){ 
				return subtreeFromIndexAndSize(x.left, getStartIndex, getSize);
			}else{ 
				WaveTree1 rightPartOfLeft = right(x.left, getStartIndex);
				WaveTree1 leftPartOfRight = left(x.right, getStartIndex+getSize-x.left.len);
				return concat(rightPartOfLeft,leftPartOfRight);
			}
		}else{ 
			return subtreeFromIndexAndSize(x.right, getStartIndex-x.left.len, getSize);
		}
	}

	
	/** same as subtree(0,getSize) but faster */
	public static WaveTree1 left(WaveTree1 x, double splitWhere){
		
		return splitAt(x,splitWhere).left;
	}

	/** same as subtree(len-getSize, len) but faster and less roundoff error */
	public static WaveTree1 right(WaveTree1 x, double splitWhere){
		
		return splitAt(x,splitWhere).right;
	}

	/** never returns a leaf */
	public static WaveTree1 splitAt(WaveTree1 x, double index){
		if(x.isLeaf()){
			return new WaveTree1(new WaveTree1(x.value1,index), new WaveTree1(x.value1,x.len-index));
		}
		if(index < x.left.len){ 
			WaveTree1 leftSplit = splitAt(x.left,index);
			return new WaveTree1(leftSplit.left, concat(leftSplit.right,x.right));
		}else{ 
			WaveTree1 rightSplit = splitAt(x.right,index-x.left.len);
			return new WaveTree1(concat(x.left,rightSplit.left), rightSplit.right);
		}
	}

	public static WaveTree1 insert(WaveTree1 x, WaveTree1 insertMe, double insertAtIndex){
		WaveTree1 s = splitAt(x,insertAtIndex);
		return concat(concat(s.left,insertMe),s.right);
	}

	public static WaveTree1 delete(WaveTree1 x, double deleteStart, double deleteEnd){
		return concat( left(x,deleteStart), right(x,deleteEnd) );
	}

	public static WaveTree1 deleteAtIndexAndSize(WaveTree1 x, double deleteStartingAtIndex, double deleteSize){
		return concat(
			left(x, deleteStartingAtIndex),
			right(x, deleteStartingAtIndex+deleteSize)
		);
	}

	public static WaveTree1 overwrite(WaveTree1 x, WaveTree1 newValue, double startOverwriteIndex){
		
		
		
		WaveTree1 a = left(x, startOverwriteIndex);
		WaveTree1 c = right(x, startOverwriteIndex+newValue.len);
		return concat(a, concat(newValue,c));
	}

	public static WaveTree1 asLeaf(WaveTree1 x){
		return x.isLeaf() ? x : new WaveTree1(x.value1,x.len);
	}

	/** for future efficiency,
	returns a new WaveTree1 with small WaveTree1 childs replaced by their parent.asLeaf()
	*/
	public static WaveTree1 withMinimumNodeSize(WaveTree1 x, double minimumNodeSize){
		
		
		
		
		
		WaveTree1 newLeft = withMinimumNodeSize(x.left,minimumNodeSize);
		WaveTree1 newRight = withMinimumNodeSize(x.right,minimumNodeSize);
		if(newLeft==x.left && newRight==x.right) return x;
		return concat(newLeft,newRight);
	}

	/** if [len:3 value1:2.2] is adjacent to [len:55 value1:2.1] and maxValueDiff >= .1,
	merges it into [len:58 value1:(weighted average of 2.2 and 2.1)].
	*/
	public static WaveTree1 withAdjacentApproxEqValuesMerged(WaveTree1 x, double maxValueDiff){
		
		
		if(x.isLeaf()){
			return x;
		}else{
			WaveTree1 leftMerged = withAdjacentApproxEqValuesMerged(x.left,maxValueDiff);
			WaveTree1 rightMerged = withAdjacentApproxEqValuesMerged(x.right,maxValueDiff);
			if(leftMerged.isLeaf() && rightMerged.isLeaf()){
				if(Math.abs(x.left.value1-x.right.value1) <= maxValueDiff) return asLeaf(x);
				if(leftMerged==x.left && rightMerged==x.right) return x;
				return concat(x.left,x.right);
			}
			return x;
		}
	}

	public static WaveTree1[] arrayOfLeafs(WaveTree1 x){
		if(x.isLeaf()){ 
			return new WaveTree1[]{x};
		}else{ 
			WaveTree1 leftArray[] = arrayOfLeafs(x.left);
			WaveTree1 rightArray[] = arrayOfLeafs(x.right);
			WaveTree1 array[] = new WaveTree1[leftArray.length+rightArray.length];
			System.arraycopy(leftArray,0,array,0,leftArray.length);
			System.arraycopy(rightArray,0,array,leftArray.length,rightArray.length);
			return array;
		}
	}

	/** if recursively any WaveTree1Ops have len 0, their parent is replaced by the nonempty child */
	public static WaveTree1 withoutEmptys(WaveTree1 x){
		if(x.isLeaf()){
			return x;
		}else{
			WaveTree1 newLeft = withoutEmptys(x.left);
			if(!newLeft.isLeaf()){
				if(newLeft.left.len == 0) newLeft = newLeft.right;
				else if(newLeft.right.len == 0) newLeft = newLeft.left;
			}
			WaveTree1 newRight = withoutEmptys(x.right);
			if(!newRight.isLeaf()){
				if(newRight.left.len == 0) newRight = newRight.right;
				else if(newRight.right.len == 0) newRight = newRight.left;
			}
			if(newLeft==x.left && newRight==x.right) return x;
			return concat(newLeft,newRight);
		}
	}

	public static WaveTree1 multiplySize(WaveTree1 x, double multiplier){
		if(x.isLeaf()){ 
			return new WaveTree1(x.value1, x.len*multiplier);
		}else{ 
			return concat(multiplySize(x.left,multiplier),multiplySize(x.right,multiplier));
		}
	}

	public static WaveTree1 reverse(WaveTree1 x){
		if(x.isLeaf()){
			return x;
		}else{
			WaveTree1 leftReversed = reverse(x.left);
			WaveTree1 rightReversed = reverse(x.right);
			if(x.left==rightReversed && x.right==leftReversed) return x; 
			return concat(rightReversed,leftReversed);
		}
	}

	public static WaveTree1 balancedByChildQuantity(WaveTree1 x){
		List list = Arrays.asList(arrayOfLeafs(x));
		while(list.size() > 1){
			List newList = new ArrayList(list.size()/2);
			for(int i=0; i<list.size()-1; i+=2){
				newList.add(concat((WaveTree1)list.get(i),(WaveTree1)list.get(i+1)));
			}
			if((list.size() & 1) == 1){
				newList.add(list.get(list.size()-1)); 
			}
			list = newList;
		}
		return (WaveTree1)list.get(0);
	}

	public static WaveTree1 balancedByValue(WaveTree1 x){
		throw new RuntimeException("code not finished");
	}

	/** Returns new double[]{value1,positionEnd1,value2,positionEnd2...}
	If sizesAreFromParent, the last double in the array is the parameter's len.
	If not sizesAreFromParent, the last double in the array
	is the parameter's len with roundoff error because the childs are summed again.
	The returned array's len is always 2 * quantity of leafs in the tree.
	*/
	public static double[] toDArray(WaveTree1 x, boolean sizesAreFromParent){
		if(sizesAreFromParent){
			throw new RuntimeException("code not finished");
		}else{
			double sizeSum = 0;
			WaveTree1 leafs[] = arrayOfLeafs(x);
			double ret[] = new double[leafs.length*2];
			for(int i=0; i<leafs.length; i++){
				ret[i+i] = leafs[i].value1;
				ret[i+i+1] = sizeSum += leafs[i].len;
			}
			return ret;
		}
	}


	public static WaveTree1 fromDArray(double d[]){
		if((d.length&1) == 1) throw new RuntimeException(
			"array len "+d.length+" is odd");
		throw new RuntimeException("code not finished");
	}

	public static void main(String s[]){
		WaveTree1 dt = newStandardDTree();
		System.out.println(WaveTree1.class.getName()+": "+dt);
		testTheStandardDTree(dt);
		dt = balancedByChildQuantity(dt);
		testTheStandardDTree(dt);
		dt = multiplySize(dt, 4.56);
		dt = multiplySize(dt, 1/4.56);
		testTheStandardDTree(dt);
		
		dt = insert(dt, new WaveTree1(2.22,3.33), 1.11);
		
		dt = deleteAtIndexAndSize(dt, 1.11, 3.33);
		
		testTheStandardDTree(dt);
		dt = insert(dt, new WaveTree1(50,60), 3.67);
		dt = insert(dt, new WaveTree1(5.2,6.3), 2.34);
		dt = deleteAtIndexAndSize(dt, 3.67+6.3, 60);
		dt = deleteAtIndexAndSize(dt, 2.34, 5);
		dt = deleteAtIndexAndSize(dt, 2.34, 1.3);
		testTheStandardDTree(dt);
		dt = withAdjacentApproxEqValuesMerged(dt, 1e-12);
		testTheStandardDTree(dt);
		dt = insert(dt, new WaveTree1(5,0), 2.46); 
		testTheStandardDTree(dt);
		String withEmpty = ""+dt;
		dt = withoutEmptys(dt);
		String withoutEmpty = ""+dt;
		System.out.println("\r\n\r\n   WITH EMPTY: "+withEmpty);
		System.out.println("WITHOUT EMPTY: "+withoutEmpty+"\r\n\r\n");
		if(withoutEmpty.length() >= withEmpty.length()) throw new RuntimeException(
			"withoutEmpty.length() >= withEmpty.length()");
		testTheStandardDTree(dt);
		
		dt = balanceAVL(dt);
		testTheStandardDTree(dt);
		
		
		System.out.println("All "+WaveTree1.class.getName()+" tests pass.");
	}

	private static WaveTree1 newStandardDTree(){
		return concat(new WaveTree1(1.,1.), concat(new WaveTree1(2.,2.), new WaveTree1(3.,3.))); 
	}

	private static void testTheStandardDTree(WaveTree1 dt){
		testSubtree(1, dt, 0, 1);
		testSubtree(2, dt, 1, 3);
		testSubtree(3, dt, 3, 6);
		testSubtree(1.666667, dt, 0, 3);
		testSubtree(2.6, dt, 1, 6);
		testSubtree(2.333333, dt, 0, 6);
		testSubtree(1, dt, -.01, .01);
		testSubtree(1.5, dt, .99, 1.01);
		testSubtree(2.5, dt, 2.99, 3.01);
		testSubtree(3, dt, 5.99, 6.01);
		testSubtree((1*.5+2*2+3*.5)/3, dt, .5, 3.5);
		WaveTree1 dt2 = concat(dt,dt);
		testSubtree((1*.5+3*.5)/1, dt2, 5.5, 6.5);
		testSubtree((3*.3+1*.7)/1, dt2, 5.7, 6.7);
	}

	private static void testSubtree(double correct, WaveTree1 dt, double from, double to){
		WaveTree1 sdt = subtree(dt,from,to);
		String s = "subtree("+dt+","+from+","+to+") == "+sdt.value1;
		System.out.println("OK: "+s);
		if(Math.abs(correct-sdt.value1) > .001) throw new RuntimeException(
			s+" but should equal "+correct);
	}

}
