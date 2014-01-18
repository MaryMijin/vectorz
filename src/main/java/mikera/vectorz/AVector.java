package mikera.vectorz;

import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import mikera.arrayz.Arrayz;
import mikera.arrayz.INDArray;
import mikera.arrayz.ISparse;
import mikera.arrayz.impl.AbstractArray;
import mikera.arrayz.impl.SliceArray;
import mikera.indexz.Index;
import mikera.matrixx.AMatrix;
import mikera.matrixx.Matrix;
import mikera.matrixx.Matrixx;
import mikera.matrixx.impl.BroadcastVectorMatrix;
import mikera.randomz.Hash;
import mikera.util.Maths;
import mikera.vectorz.impl.AArrayVector;
import mikera.vectorz.impl.ImmutableVector;
import mikera.vectorz.impl.JoinedVector;
import mikera.vectorz.impl.ListWrapper;
import mikera.vectorz.impl.Vector0;
import mikera.vectorz.impl.VectorIndexScalar;
import mikera.vectorz.impl.VectorIterator;
import mikera.vectorz.impl.WrappedSubVector;
import mikera.vectorz.ops.Logistic;
import mikera.vectorz.util.ErrorMessages;
import mikera.vectorz.util.VectorzException;

/**
 * Main abstract base class for all types of vector
 * 
 * Contains default implementations for most vector operations which can be
 * overriden to achieve better performance in derived classes.
 * 
 * @author Mike
 *
 */
@SuppressWarnings("serial")
public abstract class AVector extends AbstractArray<Double> implements IVector, Comparable<AVector> {
	
	// ================================================
	// Abstract interface

	@Override
	public abstract int length();

	@Override
	public abstract double get(int i);
	
	@Override
	public abstract void set(int i, double value);
	
	// ================================================
	// Standard implementations

	public double get(long i) {
		return get((int)i);
	}
	
	public void set(long i, double value) {
		set((int)i,value);
	}
	
	@Override
	public void set(int[] indexes, double value) {
		if (indexes.length==1) {
			set(indexes[0],value);
		} if (indexes.length==0) {
			fill(value);
		} else {
			throw new UnsupportedOperationException(""+indexes.length+"D set not supported on AVector");
		}
	}
	
	public void unsafeSet(int i, double value) {
		set(i,value);
	}
	
	public double unsafeGet(int i) {
		return get(i);
	}
	
	protected void unsafeSetInteger(Integer i,double value) {
		unsafeSet((int)i,value);
	}
	
	protected double unsafeGetInteger(Integer i) {
		return unsafeGet((int)i);
	}
	
	@Override
	public double get(int x, int y) {
		throw new IllegalArgumentException(ErrorMessages.invalidIndex(this, x,y));
	}
	
	@Override
	public final int dimensionality() {
		return 1;
	}
	
	@Override
	public double get(int... indexes) {
		if (indexes.length!=1) throw new IllegalArgumentException(ErrorMessages.invalidIndex(this, indexes));
		return get(indexes[0]);
	}
	
	@Override
	public double get() {
		throw new UnsupportedOperationException("Can't do 0-d get on a vector!");
	}
	
	@Override
	public AScalar slice(int position) {
		return VectorIndexScalar.wrap(this,position);
	}
	
	@Override
	public AScalar slice(int dimension, int index) {
		if (dimension!=0) throw new IllegalArgumentException(ErrorMessages.invalidDimension(this, dimension));
		return slice(index);	
	}	
	
	@Override
	public int sliceCount() {
		return length();
	}
	
	@Override
	public List<Double> getSlices() {
		// TODO: consider returning a ListWrapper directly?
		ArrayList<Double> al=new ArrayList<Double>();
		int l=length();
		for (int i=0; i<l; i++) {
			al.add(unsafeGet(i));
		}
		return al;
	}
	
	@Override
	public int[] getShape() {
		return new int[] {length()};
	}
	
	@Override
	public int[] getShapeClone() {
		return new int[] {length()};
	}
	
	@Override
	public int getShape(int dim) {
		if (dim==0) {
			return length();
		} else {
			throw new IndexOutOfBoundsException(ErrorMessages.invalidDimension(this, dim));
		}
	}

	
	@Override
	public long[] getLongShape() {
		return new long[] {length()};
	}
	
	@Override
	public long elementCount() {
		return length();
	}
	
	@Override
	public long nonZeroCount() {
		int n=length();
		long result=0;
		for (int i=0; i<n; i++) {
			if (unsafeGet(i)!=0.0) result++;
		}
		return result;
	}
	
	@Override
	public AVector subArray(int[] offsets, int[] shape) {
		if (offsets.length!=1) throw new IllegalArgumentException(ErrorMessages.invalidIndex(this, offsets));
		if (shape.length!=1) throw new IllegalArgumentException(ErrorMessages.invalidIndex(this, offsets));
		return subVector(offsets[0],shape[0]);
	}
	
	
	@Override
	public INDArray rotateView(int dimension, int shift) {
		if (dimension!=0) throw new IllegalArgumentException(ErrorMessages.invalidDimension(this, dimension));
		return rotateView(shift);
	}
	
	public INDArray rotateView(int shift) {
		int n=length();
		if (n==0) return this;
		
		shift = Maths.mod(shift,n);
		if (shift==0) return this;
			
		return subVector(shift,n-shift).join(subVector(0,shift));
	}	
	
	/**
	 * Obtains a sub-vector that refers to this vector.
	 * Changes to the sub-vector will be reflected in this vector
	 */
	public AVector subVector(int offset, int length) {
		int len=this.length();
		if ((offset<0)||(offset+length>len)) {
			throw new IndexOutOfBoundsException(ErrorMessages.invalidRange(this, offset, length));
		}

		if (length==0) return Vector0.INSTANCE;
		if (length==len) return this;
		
		return new WrappedSubVector(this,offset,length);
	}

	/**
	 * Returns a new vector that refers to this vector joined to a second vector
	 * @param second
	 * @return
	 */
	public AVector join(AVector second) {
		return JoinedVector.joinVectors(this,second);
	}
	
	@Override
	public INDArray join(INDArray a, int dimension) {
		if (dimension!=0) throw new IllegalArgumentException(ErrorMessages.invalidDimension(this, dimension));
		if (a instanceof AVector) {
			return join((AVector)a);
		}
		if (a.dimensionality()!=1) throw new IllegalArgumentException(ErrorMessages.incompatibleShapes(this, a));
		return join(a.asVector());
	}
	
	@Override
	public int compareTo(AVector a) {
		int len=length();
		if (len!=a.length()) throw new IllegalArgumentException("Vectors must be same length for comparison");
		for (int i=0; i<len; i++) {
			double diff=get(i)-a.get(i);
			if (diff<0.0) return -1;
			if (diff>0.0) return 1;
		}
		return 0;
	}
	
	/**
	 * Test for equality on vectors. Returns true iff all values in the vector
	 * are identical
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o instanceof AVector) return equals((AVector)o);
		if (o instanceof INDArray) return equals((INDArray)o);
		return false;
	}
	
	public boolean equals(AVector v) {
		if (this==v) return true;
		int len=length();
		if (len != v.length())
			return false;
		for (int i = 0; i < len; i++) {
			if (unsafeGet(i) != v.unsafeGet(i))
				return false;
		}
		return true;
	}
	
	@Override
	public boolean equals(INDArray v) {
		if (v instanceof AVector) return equals((AVector)v);
		if (v.dimensionality()!=1) return false;
		int len=length();
		if (len != v.getShape()[0]) return false;
		
		int[] ind = new int[1];
		for (int i = 0; i < len; i++) {
			ind[0]=i;
			if (unsafeGet(i) != v.get(ind))
				return false;
		}
		return true;
	}
	
	public List<Double> toList() {
		ArrayList<Double> al=new ArrayList<Double>();
		int len=length();
		for (int i=0; i<len; i++) {
			al.add(unsafeGet(i));
		}
		return al;
	}
	
	@Override
	public boolean epsilonEquals(INDArray a, double tolerance) {
		if (a instanceof AVector) return epsilonEquals((AVector)a);
		if (a.dimensionality()!=1) throw new IllegalArgumentException(ErrorMessages.incompatibleShapes(this, a));
		int len=length();
		if (len!=a.getShape(0)) throw new IllegalArgumentException(ErrorMessages.incompatibleShapes(this, a));
		for (int i = 0; i < len; i++) {
			if (!Tools.epsilonEquals(unsafeGet(i), a.get(i), tolerance)) return false;
		}		
		return true;
	}
	
	@Override
	public boolean epsilonEquals(INDArray a) {
		return epsilonEquals(a,Vectorz.TEST_EPSILON);
	}
	
	public boolean epsilonEquals(AVector v) {
		return epsilonEquals(v,Vectorz.TEST_EPSILON);
	}
	
	public boolean epsilonEquals(AVector v,double tolerance) {
		if (this == v) return true;
		int len=length();
		if (len!=v.length())
			throw new IllegalArgumentException(ErrorMessages.incompatibleShapes(this, v));
		for (int i = 0; i < len; i++) {
			if (!Tools.epsilonEquals(unsafeGet(i), v.unsafeGet(i), tolerance)) return false;
		}
		return true;
	}
	
	/**
	 * Computes the hashcode of a vector.
	 * 
	 * Currently defined to be equal to List.hashCode for a equivalent list of Double values, 
	 * this may change in future versions.
	 */
	@Override
	public int hashCode() {
		int hashCode = 1;
		int len=length();
		for (int i = 0; i < len; i++) {
			hashCode = 31 * hashCode + (Hash.hashCode(unsafeGet(i)));
		}
		return hashCode;
	}

	@Override
	public void copyTo(double[] arr) {
		getElements(arr,0);
	}
	
	public final void copyTo(double[] arr, int offset) {
		getElements(arr,offset);
	}
	
	public void copyTo(int offset, double[] dest, int destOffset, int length) {
		for (int i=0; i<length; i++) {
			dest[i+destOffset]=unsafeGet(i+offset);
		}
	}
	
	public double[] toDoubleArray() {
		double[] result=new double[length()];
		getElements(result,0);
		return result;
	}
	
	@Override
	public double[] asDoubleArray() {
		return null;
	}
	
	@Override
	public void toDoubleBuffer(DoubleBuffer dest) {
		int len=length();
		for (int i=0; i<len; i++) {
			dest.put(unsafeGet(i));
		}
	}
	
	/**
	 * Copies a the contents of a vector to a vector at the specified offset
	 */
	public void copyTo(AVector dest, int destOffset) {
		if (dest instanceof AArrayVector) {
			copyTo((AArrayVector) dest,destOffset);
			return;
		}
		int len = length();
		if (destOffset+len>dest.length()) throw new IndexOutOfBoundsException();
		for (int i=0; i<len; i++) {
			dest.unsafeSet(destOffset+i,unsafeGet(i));
		}
	}
	
	/**
	 * Copies a the contents of a vector to a vector at the specified offset
	 */
	public void copyTo(AArrayVector dest, int destOffset) {
		getElements(dest.getArray(),dest.getArrayOffset()+destOffset);
	}
	
	/**
	 * Copies a subset of this vector to a vector at the specified offset
	 */
	public void copyTo(int offset, AVector dest, int destOffset, int length) {
		for (int i=0; i<length; i++) {
			dest.set(destOffset+i,get(offset+i));
		}
	}

	/**
	 * Fills the entire vector with a given value
	 * @param value
	 */
	public void fill(double value) {
		fillRange(0,length(),value);
	}
	
	public void fillRange(int offset, int length, double value) {
		if ((offset<0)||(offset+length>length())) throw new IndexOutOfBoundsException();
		for (int i = 0; i < length; i++) {
			unsafeSet(i+offset,value);
		}
	}
	
	/**
	 * Clamps all values in the vector to a given range
	 * @param value
	 */
	@Override
	public void clamp(double min, double max) {
		int len=length();
		for (int i = 0; i < len; i++) {
			double v=unsafeGet(i);
			if (v<min) {
				unsafeSet(i,min);
			} else if (v>max) {
				unsafeSet(i,max);
			}
		}
	}
	
	public void clampMax(double max) {
		int len=length();
		for (int i = 0; i < len; i++) {
			double v=unsafeGet(i);
			if (v>max) {
				unsafeSet(i,max);
			}
		}
	}
	
	public void clampMin(double min) {
		int len=length();
		for (int i = 0; i < len; i++) {
			double v=unsafeGet(i);
			if (v<min) {
				unsafeSet(i,min);
			} 
		}
	}
	
	/**
	 * Multiplies the vector by a constant factor
	 * @param factor Factor by which to multiply each component of the vector
	 */
	public void multiply(double factor) {
		int len=length();
		for (int i = 0; i < len; i++) {
			unsafeSet(i,unsafeGet(i)*factor);
		}	
	}
	
	public void multiply(INDArray a) {
		if (a instanceof AVector) {
			multiply((AVector)a);
		} else if (a instanceof AScalar) {
			multiply(((AScalar)a).get());
		} else {
			int dims=a.dimensionality();
			switch (dims) {
				case 0: multiply(a.get()); return;
				case 1: multiply(a.asVector()); return;
				default: throw new VectorzException("Can't multiply vector with array of dimensionality: "+dims);
			}
		}
	}
	
	public void multiply(AVector v) {
		int len=length();
		if (len!=v.length()) throw new IllegalArgumentException(ErrorMessages.incompatibleShapes(this, v));
		for (int i = 0; i < len; i++) {
			unsafeSet(i,unsafeGet(i)*v.unsafeGet(i));
		}	
	}
	
	public void multiply(double[] data, int offset) {
		int len=length();
		for (int i = 0; i < len; i++) {
			unsafeSet(i,unsafeGet(i)*data[i+offset]);
		}	
	}
	
	public void multiplyTo(double[] data, int offset) {
		int len=length();
		for (int i = 0; i < len; i++) {
			data[i+offset]*=unsafeGet(i);
		}	
	}
	
	public void divide(double factor) {
		multiply(1.0/factor);
	}
	
	@Override
	public void divide(INDArray a) {
		if (a instanceof AVector) {
			divide((AVector)a);
		} else {
			super.divide(a);
		}
	}
	
	public void divide(AVector v) {
		int len=length();
		if (len!=v.length()) throw new IllegalArgumentException(ErrorMessages.incompatibleShapes(this, v));
		for (int i = 0; i < len; i++) {
			unsafeSet(i,unsafeGet(i)/v.unsafeGet(i));
		}	
	}
	
	public void divide(double[] data, int offset) {
		int len=length();
		for (int i = 0; i < len; i++) {
			unsafeSet(i,unsafeGet(i)/data[i+offset]);
		}	
	}
	
	public void divideTo(double[] data, int offset) {
		int len=length();
		for (int i = 0; i < len; i++) {
			data[i+offset]/=unsafeGet(i);
		}	
	}
	
	/**
	 * Sets each component of the vector to its absolute value
	 */
	public void abs() {
		int len=length();
		for (int i=0; i<len; i++) {
			double val=unsafeGet(i);
			if (val<0) unsafeSet(i,-val);
		}
	}
	
	@Override
	public void log() {
		int len=length();
		for (int i=0; i<len; i++) {
			double val=unsafeGet(i);
			unsafeSet(i,Math.log(val));
		}
	}
	
	/**
	 * Sets each component of the vector to its sign value (-1, 0 or 1)
	 */
	@Override
	public void signum() {
		int len=length();
		for (int i=0; i<len; i++) {
			unsafeSet(i,Math.signum(unsafeGet(i)));
		}
	}
	
	/**
	 * Squares all elements of the vector
	 */
	@Override
	public void square() {
		int len=length();
		for (int i=0; i<len; i++) {
			double x=unsafeGet(i);
			unsafeSet(i,x*x);
		}		
	}
	
	public void tanh() {
		int len=length();
		for (int i=0; i<len; i++) {
			double x=unsafeGet(i);
			unsafeSet(i,Math.tanh(x));
		}			
	}
	
	public void logistic() {
		int len=length();
		for (int i=0; i<len; i++) {
			double x=unsafeGet(i);
			unsafeSet(i,Logistic.logisticFunction(x));
		}			
	}
	
	/**
	 * Scales the vector by another vector of the same size
	 * @param v
	 */
	public final void scale(AVector v) {
		multiply(v);
	}
	
	/**
	 * Scales the vector up to a specific target magnitude
	 * @return the old magnitude of the vector
	 */
	public double scaleToMagnitude(double targetMagnitude) {
		double oldMagnitude=magnitude();
		multiply(targetMagnitude/oldMagnitude);
		return oldMagnitude;
	}
	
	public void scaleAdd(double factor, AVector v) {
		multiply(factor);
		add(v);
	}
	
	public void interpolate(AVector v, double alpha) {
		multiply(1.0-alpha);
		addMultiple(v,alpha);
	}
	
	public void interpolate(AVector a, AVector b, double alpha) {
		set(a);
		interpolate(b,alpha);
	}
	
	public double magnitudeSquared() {
		int len=length();
		double total=0.0;
		for (int i=0; i<len; i++) {
			double x=unsafeGet(i);
			total+=x*x;
		}
		return total;
	}
	
	@Override
	public AVector getTranspose() {return this;}
	
	@Override
	public Vector getTransposeCopy() {
		return Vector.create(this);
	}
	
	@Override
	public final AVector getTransposeView() {return this;}
	
	public AMatrix outerProduct(AVector a) {
		int rc=length();
		int cc=a.length();
		Matrix m=Matrix.create(rc, cc);
		int di=0;
		for (int i=0; i<rc; i++) {
			for (int j=0; j<cc; j++) {
				m.data[di++]=unsafeGet(i)*a.unsafeGet(j);
			}
		}
		return m;
	}
	
	public INDArray outerProduct(INDArray a) {
		if (a instanceof AVector) {
			return outerProduct((AVector)a);
		}
		return super.outerProduct(a);
	}
	
	public Scalar innerProduct(AVector v) {
		return Scalar.create(dotProduct(v));
	}

	public Scalar innerProduct(Vector v) {
		int vl=v.data.length;
		if (length()!=vl) throw new IllegalArgumentException(ErrorMessages.incompatibleShapes(this, v));
		return Scalar.create(dotProduct(v.data,0));
	}
	
	public AVector innerProduct(AMatrix m) {
		int cc=m.columnCount();
		int rc=m.rowCount();
		if (rc!=length()) throw new VectorzException("Incompatible sizes for inner product: ["+length()+ "] x ["+rc+","+cc+"]");
		Vector r=Vector.createLength(cc);
		for (int i=0; i<cc; i++) {
			double y=0.0;
			for (int j=0; j<rc; j++) {
				y+=unsafeGet(j)*m.unsafeGet(j,i);
			}
			r.unsafeSet(i,y);
		}
		return r;
	}
	
	public AVector innerProduct(AScalar s) {
		Vector v=toVector();
		v.scale(s.get());
		return v;
	}
	
	public INDArray innerProduct(INDArray a) {
		if (a instanceof AVector) {
			return Scalar.create(dotProduct((AVector)a));
		} else if (a instanceof AScalar) {
			return innerProduct((AScalar)a);
		} else if (a instanceof AMatrix) {
			return innerProduct((AMatrix)a);
		}
		return super.innerProduct(a);
	}
	
	public double dotProduct(AVector v) {
		if (v instanceof Vector) return dotProduct((Vector)v);
		int len=length();
		if(v.length()!=len) throw new IllegalArgumentException("Vector size mismatch");
		double total=0.0;
		for (int i=0; i<len; i++) {
			total+=unsafeGet(i)*v.unsafeGet(i);
		}
		return total;
	}
	
	public double dotProduct(Vector v) {
		if(v.length()!=length()) throw new IllegalArgumentException("Vector size mismatch");
		return dotProduct(v.data, 0);
	}
	
	public double dotProduct(AVector v, Index ix) {
		int vl=v.length();
		if (v.length()!=ix.length()) throw new IllegalArgumentException("Mismtached source vector and index sizes");
		double result=0.0;
		for (int i=0; i<vl; i++) {
			result+=unsafeGet(ix.get(i))*v.unsafeGet(i);
		}
		return result;
	}
	
	/**
	 * Fast dot product with a double[] array. Performs no bounds checking.

	 * @param data
	 * @param offset
	 * @return
	 */
	public double dotProduct(double[] data, int offset) {
		int len=length();
		double result=0.0;
		for (int i=0; i<len; i++) {
			result+=unsafeGet(i)*data[offset+i];
		}
		return result;
	}
	
	public void crossProduct(AVector a) {
		if(!((length()==3)&&(a.length()==3))) throw new IllegalArgumentException("Cross product requires length 3 vectors");
		double x=unsafeGet(0);
		double y=unsafeGet(1);
		double z=unsafeGet(2);
		double x2=a.unsafeGet(0);
		double y2=a.unsafeGet(1);
		double z2=a.unsafeGet(2);
		double tx=y*z2-z*y2;
		double ty=z*x2-x*z2;
		double tz=x*y2-y*x2;			
		set(0,tx);
		set(1,ty);
		set(2,tz);		
	}
	
	/**
	 * Returns the magnitude (Euclidean length) of the vector
	 * @return
	 */
	public double magnitude() {
		return Math.sqrt(magnitudeSquared());
	}
	
	public double distanceSquared(AVector v) {
		int len=length();
		double total=0.0;
		for (int i=0; i<len; i++) {
			double d=unsafeGet(i)-v.unsafeGet(i);
			total+=d*d;
		}
		return total;
	}
	
	public double distance(AVector v) {
		return Math.sqrt(distanceSquared(v));
	}
	
	public double distanceL1(AVector v) {
		int len=length();
		double total=0.0;
		for (int i=0; i<len; i++) {
			double d=unsafeGet(i)-v.unsafeGet(i);
			total+=Math.abs(d);
		}
		return total;
	}
	
	public double distanceLinf(AVector v) {
		int len=length();
		double result=0.0;
		for (int i=0; i<len; i++) {
			double d=Math.abs(unsafeGet(i)-v.unsafeGet(i));
			result=Math.max(result,d);
		}
		return result;
	}
	
	/**
	 * Returns the maximum absolute element of a vector
	 * @return
	 */
	public double maxAbsElement() {
		int len=length();
		double result=0.0;
		for (int i=0; i<len; i++) {
			double comp=Math.abs(unsafeGet(i));
			if (comp>result) {
				result=comp;
			} 
		}		
		return result;
	}
	
	/**
	 * Returns the index of the maximum absolute element of a vector
	 * @return
	 */
	public int maxAbsElementIndex() {
		int len=length();
		if (len==0) throw new IllegalArgumentException("Can't find maxAbsElementIndex of a 0-length vector");
		int result=0;
		double best=Math.abs(unsafeGet(0));
		for (int i=1; i<len; i++) {
			double comp=Math.abs(unsafeGet(i));
			if (comp>best) {
				result=i;
				best=comp;
			} 
		}		
		return result;
	}
	
	/**
	 * Returns the maximum element of a vector. Synonym for elementMax()
	 * @return
	 */
	public final double maxElement() {
		return elementMax();
	}
	
	/**
	 * Returns the index of the maximum element of a vector
	 * @return
	 */
	public int maxElementIndex() {
		int len=length();
		if (len==0) throw new IllegalArgumentException("Can't find maxElementIndex of a 0-length vector");
		int result=0;
		double best=unsafeGet(0);
		for (int i=1; i<len; i++) {
			double comp=unsafeGet(i);
			if (comp>best) {
				result=i;
				best=comp;
			} 
		}		
		return result;
	}
	
	/**
	 * Returns the minimum element of a vector. Synonym for elementMax()
	 * @return
	 */
	public final double minElement() {
		return elementMin();
	}
	
	/**
	 * Returns the index of the minimum element of a vector
	 * @return
	 */
	public int minElementIndex() {
		int len=length();
		if (len==0) throw new IllegalArgumentException("Can't find minElementIndex of a 0-length vector");
		int result=0;
		double best=unsafeGet(0);
		for (int i=1; i<len; i++) {
			double comp=unsafeGet(i);
			if (comp<best) {
				result=i;
				best=comp;
			} 
		}		
		return result;
	}
	
	/**
	 * Normalises so that the maximum absolute element is 1.0
	 * Returns the previous maximum absolute element.
	 */
	public double normaliseMaxAbsElement() {
		double scale=maxAbsElement();
		if (scale!=0.0) scale(1.0/scale);
		return scale;
	}
	
	/**
	 * Returns the sum of all elements in a vector
	 * @return
	 */
	@Override
	public double elementSum() {
		int len=length();
		double result=0.0;
		for (int i=0; i<len; i++) {
			result+=unsafeGet(i);
		}		
		return result;
	}
	
	@Override
	public double elementMax(){
		int len=length();
		double max = -Double.MAX_VALUE;
		for (int i=0; i<len; i++) {
			double d=unsafeGet(i);
			if (d>max) max=d;
		}
		return max;
	}
	
	@Override
	public double elementMin(){
		int len=length();
		double min = Double.MAX_VALUE;
		for (int i=0; i<len; i++) {
			double d=unsafeGet(i);
			if (d<min) min=d;
		}
		return min;
	}
	
	@Override public final double elementSquaredSum() {
		return magnitudeSquared();
	}
	
	/**
	 * Returns the Euclidean angle between this vector and another vector
	 * @return angle in radians
	 */
	public double angle(AVector v) {
		return Math.acos(dotProduct(v)/(v.magnitude()*this.magnitude()));
	}
	
	/**
	 * Normalises this vector to a magnitude of 1.0
	 * 
	 * Has no effect on a zero-length vector (i.e. it will remain zero)
	 * 
	 * @return
	 */
	public double normalise() {
		double d=magnitude();
		if (d>0) multiply(1.0/d);
		return d;
	}
	
	public void negate() {
		multiply(-1.0);
	}
	
	@Override
	public void pow(double exponent) {
		int len=length();
		for (int i=0; i<len; i++) {
			unsafeSet(i,Math.pow(unsafeGet(i),exponent));
		}				
	}
	
	/**
	 * Sets the vector to equal the value of another vector
	 */
	public void set(AVector src) {
		int len=length();
		if (src.length()!=len) throw new IllegalArgumentException("Source Vector of wrong size: "+src.length());
		for (int i=0; i<len; i++) {
			unsafeSet(i,src.unsafeGet(i));
		}
	}
	
	public final void set(double a) {
		throw new UnsupportedOperationException("0d set not supported for vectors - use fill instead?");
	}
	
	@Deprecated
	public void set(double[] data) {
		setElements(data,0,length());
	}
	
	@Override
	public void setElements(double[] data) {
		setElements(data,0,length());
	}
	
	public void set(INDArray a) {
		if (a instanceof AVector) {set((AVector)a); return;}
		if (a.dimensionality()==1) {
			int len=length();
			for (int i=0; i<len; i++) {
				unsafeSet(i,a.get(i));
			}		
		} else {
			throw new IllegalArgumentException("Cannot set vector using array of dimensonality: "+a.dimensionality());
		}
	}
	
	@Override
	public void setElements(double[] values, int offset, int length) {
		if (length!=length()) {
			throw new IllegalArgumentException("Incorrect length: "+length);
		}
		for (int i=0; i<length; i++) {
			unsafeSet(i,values[offset+i]);
		}
	}
	
	@Override
	public void getElements(double[] dest, int offset) {
		copyTo(0,dest,offset,length());
	}
	
	/**
	 * Set the vector equal to an offset into another vector
	 * @param src
	 * @param srcOffset
	 */
	public void set(AVector src, int srcOffset) {
		int len=length();
		if ((srcOffset<0)||(len+srcOffset>src.length())) throw new IndexOutOfBoundsException();
		for (int i=0; i<len; i++) {
			unsafeSet(i,src.unsafeGet(srcOffset+i));
		}
	}
	
	public void setValues(double... values) {
		int len=length();
		if (values.length!=len) throw new VectorzException("Trying to set vectors with incorrect number of doubles: "+values.length);
		for (int i=0; i<len; i++) {
			unsafeSet(i,values[i]);
		}		
	}
	
	public long zeroCount() {
		return elementCount()-nonZeroCount();
	}
	
	/**
	 * Clones the vector, creating a new mutable copy of all data. 
	 * 
	 * The clone is:
	 *  - not guaranteed to be of the same type. 
	 *  - guaranteed to be fully mutable
	 *  - guaranteed not to contain a reference (i.e. is a full deep copy)
	 */
	@Override
	public AVector clone() {
		return Vector.create(this);
	}
	
	@Override
	public final AVector asVector() {
		return this;
	}
	
	@Override
	public INDArray reshape(int... dimensions) {
		int ndims=dimensions.length;
		if (ndims==1) {
			return Vector.createFromVector(this, dimensions[0]);
		} else if (ndims==2) {
			return Matrixx.createFromVector(this, dimensions[0], dimensions[1]);
		} else {
			return Arrayz.createFromVector(this,dimensions);
		}
	}
	
	/**
	 * Returns true if this vector is of a view type that references other vectors / data.
	 * @return
	 */
	@Override
	public boolean isView() {
		return true;
	}
	
	/**
	 * Returns true if this vector is mutable.
	 * @return
	 */
	@Override
	public boolean isMutable() {
		return true;
	}
	
	@Override
	public boolean isElementConstrained() {
		return false;
	}
	
	
	/**
	 * Returns true if this vector is fully mutable, i.e. can contain any unconstrained double values
	 * @return
	 */
	@Override
	public boolean isFullyMutable() {
		return isMutable();
	}
	
	/**
	 * Adds another vector to this one
	 * @param v
	 */
	public void add(AVector v) {
		int vlength=v.length();
		int length=length();
		if (vlength != length) {
			throw new IllegalArgumentException("Source vector has different size: " + vlength);
		}
		for (int i = 0; i < length; i++) {
			addAt(i,v.unsafeGet(i));
		}
	}
	
	@Override
	public void add(INDArray a) {
		if (a instanceof AVector) {
			add((AVector)a);
		} else if (a instanceof AScalar) {
			add(a.get());
		}else {
			super.add(a);
		}
	}
	
	@Override
	public void sub(INDArray a) {
		if (a instanceof AVector) {
			sub((AVector)a);
		} else if (a instanceof AScalar) {
			sub(a.get());
		}else {
			super.sub(a);
		}	
	}
	
	/**
	 * Adds part another vector to this one, starting at the specified offset in the source vector
	 * @param src
	 */
	public void add(AVector src, int srcOffset) {
		int length=length();
		if (!((srcOffset>=0)&&(srcOffset+length<=src.length()))) throw new IndexOutOfBoundsException();
		for (int i = 0; i < length; i++) {
			addAt(i,src.unsafeGet(srcOffset+i));
		}
	}
	
	/**
	 * Adds another vector into this one, at the specified offset
	 * @param offset
	 * @param a
	 */
	public void add(int offset, AVector a) {
		add(offset,a,0,a.length());
	}
	
	/**
	 * Adds another vector into this one, at the specified offset
	 * @param offset
	 * @param a
	 */
	public void add(int offset, AVector a, int aOffset, int length) {
		for (int i = 0; i < length; i++) {
			addAt(offset+i,a.unsafeGet(i+aOffset));
		}		
	}
	
	public void addProduct(AVector a, AVector b) {
		addProduct(a,b,1.0);
	}
	
	public void addProduct(AVector a, AVector b, double factor) {
		int length=length();
		if((a.length()!=length)||(b.length()!=length)) {
			throw new IllegalArgumentException("Unequal vector sizes for addProduct");
		}
		for (int i = 0; i < length; i++) {
			addAt(i,(a.unsafeGet(i)*b.unsafeGet(i)*factor));
		}
	}
	
	/**
	 * Adds a scaled multiple of another vector to this one
	 * @param src
	 */
	public void addMultiple(AVector src, double factor) {
		if (src.length()!=length()) throw new RuntimeException("Source vector has different size!" + src.length());
		addMultiple(src,0,factor);
	}
	
	public void addMultiple(AVector src, int srcOffset, double factor) {
		addMultiple(0,src,srcOffset,length(),factor);
	}
	
	public void addMultiple(int offset, AVector src, int srcOffset, int length, double factor) {
		if ((offset+length)>length()) throw new IndexOutOfBoundsException(ErrorMessages.invalidRange(this, offset, length));
		if ((srcOffset<0)||(srcOffset+length>src.length())) throw new IndexOutOfBoundsException(ErrorMessages.invalidRange(src, srcOffset, length));
		for (int i = 0; i < length; i++) {
			addAt(i+offset,src.unsafeGet(i+srcOffset)*factor);
		}
	}
	
	public void addMultiple(int offset, AVector v, double factor) {
		addMultiple(offset,v,0,v.length(),factor);
	}
	
	/**
	 * Updates a weighted average of this vector with another vector
	 * @param v
	 */
	public void addWeighted(AVector v, double factor) {
		multiply(1.0-factor);
		addMultiple(v,factor);
	}
	
	/**
	 * Subtracts a vector from this vector
	 * @param v
	 */
	public void sub(AVector v) {
		addMultiple(v,-1.0);
	}
	
	@Override
	public void sub(double d) {
		add(-d);
	}
	
	/**
	 * Returns true if this vector is a zero vector (all components zero)
	 * @return
	 */
	public boolean isZero() {
		int len=length();
		for (int i=0; i<len; i++) {
			if (unsafeGet(i)!=0.0) return false;
		}
		return true;
	}
	
	/**
	 * Returns true if the vector has unit length
	 * @return
	 */
	public boolean isUnitLengthVector() {
		double mag=magnitudeSquared();
		return Math.abs(mag-1.0)<Vectorz.TEST_EPSILON;
	}
	
	@Override
	public boolean isSameShape(INDArray a) {
		if (a instanceof AVector) return isSameShape((AVector)a);
		if (a.dimensionality()!=1) return false;
		return length()==a.getShape(0);
	}
	
	public boolean isSameShape(AVector a) {
		return length()==a.length();
	}
	
	public void projectToPlane(AVector normal, double distance) {
		assert(Tools.epsilonEquals(normal.magnitude(), 1.0));
		double d=dotProduct(normal);
		addMultiple(normal,distance-d);
	}
	
	/**
	 * Subtracts a scaled multiple of another vector from this vector
	 * @param v
	 */
	public void subMultiple(AVector v, double factor) {
		addMultiple(v,-factor);
	}
	
	@Override
	public String toString() {
		StringBuilder sb=new StringBuilder();
		int length=length();
		sb.append('[');
		if (length>0) {
			sb.append(unsafeGet(0));
			for (int i = 1; i < length; i++) {
				sb.append(',');
				sb.append(unsafeGet(i));
			}
		}
		sb.append(']');
		return sb.toString();
	}
	
	@Override
	public Vector toVector() {
		return Vector.create(this);
	}
	
	/**
	 * Creates an immutable copy of a vector
	 * @return
	 */
	@Override
	public AVector immutable() {
		if (!isMutable()) return this;
		return ImmutableVector.create(this);
	}
	
	/**
	 * Coerces to a mutable version of a vector. May or may not be a copy,
	 * but guaranteed to be fully mutable
	 * @return
	 */
	@Override
	public AVector mutable() {
		if (this.isFullyMutable()) {
			return this;
		} else {
			return clone();
		}
	}
	
	@Override
	public AVector sparse() {
		if (this instanceof ISparse) return this;
		return Vectorz.createSparse(this);
	}
	
	/**
	 * Creates a new mutable vector representing the normalised value of this vector
	 * @return
	 */
	public AVector toNormal() {
		Vector v= Vector.create(this);
		v.normalise();
		return v;
	}
	
	public List<Double> asElementList() {
		return new ListWrapper(this);
	}
	
	@Override
	public Iterator<Double> iterator() {
		return new VectorIterator(this);
	}
	
	@Override
	public Iterator<Double> elementIterator() {
		return iterator();
	}

	public void set(IVector vector) {
		int len=length();
		if (len!=vector.length()) throw new IllegalArgumentException(ErrorMessages.mismatch(this, vector));
		for (int i=0; i<len; i++) {
			this.unsafeSet(i,vector.get(i));
		}
	}

	/**
	 * Adds source vector to this vector at the specified indexes which should map from source->this
	 * @param source
	 * @param sourceToDest
	 * @param factor
	 */
	public void addMultiple(Vector source, Index sourceToDest, double factor) {
		if (sourceToDest.length()!=source.length()) throw new VectorzException("Index must match source vector");
		int len=source.length();
		if (len!=sourceToDest.length()) throw new IllegalArgumentException("Index length must match source length.");
		for (int i=0; i<len; i++) {
			int j=sourceToDest.data[i];
			this.addAt(j,source.data[i]*factor);
		}
	}
	
	/**
	 * Adds source vector to this vector at the specified indexes which should map from source->this
	 * @param source
	 * @param sourceToDest
	 * @param factor
	 */
	public void addMultiple(AVector source, Index sourceToDest, double factor) {
		if (sourceToDest.length()!=source.length()) throw new VectorzException("Index must match source vector");
		int len=source.length();
		if (len!=sourceToDest.length()) throw new IllegalArgumentException("Index length must match source length.");
		for (int i=0; i<len; i++) {
			int j=sourceToDest.data[i];
			this.addAt(j,source.unsafeGet(i)*factor);
		}
	}
	
	/**
	 * Adds to this vector at taking values from source at the specified indexes which should map from this->source
	 * @param source
	 * @param destToSource
	 * @param factor
	 */
	public void addMultiple(Index destToSource, Vector source, double factor) {
		if (destToSource.length()!=this.length()) throw new VectorzException("Index must match this vector");
		int len=this.length();
		if (len!=destToSource.length()) throw new IllegalArgumentException("Index length must match this vector length.");
		for (int i=0; i<len; i++) {
			int j=destToSource.data[i];
			this.addAt(i,source.data[j]*factor);
		}
	}
	
	/**
	 * Adds to this vector at taking values from source at the specified indexes which should map from this->source
	 * @param source
	 * @param destToSource
	 * @param factor
	 */
	public void addMultiple(Index destToSource, AVector source, double factor) {
		int len=this.length();
		if (len!=destToSource.length()) throw new IllegalArgumentException("Index length must match this vector length.");
		for (int i=0; i<len; i++) {
			int j=destToSource.data[i];
			this.addAt(i,source.get(j)*factor);
		}
	}

	/**
	 * sets the vector using values indexed from another vector
	 */
	public void set(AVector v, Index indexes) {
		int len=length();
		if (len!=indexes.length()) throw new IllegalArgumentException("Index length must match this vector length.");
		for (int i=0; i<len ; i++) {
			unsafeSet(i, v.get(indexes.unsafeGet(i)));
		}
	}
	
	/**
	 * Adds this vector to a double[] array, starting at the specified offset.
	 * 
	 * @param array
	 * @param offset
	 */
	public void addToArray(double[] array, int offset) {
		addToArray(0,array,offset,length());
	}

	public void addToArray(int offset, double[] array, int arrayOffset, int length) {
		if((offset<0)||(offset+length>length())) throw new IndexOutOfBoundsException();
		for (int i=0; i<length; i++) {
			array[i+arrayOffset]+=unsafeGet(i+offset);
		}
	}
	
	public void addMultipleToArray(double factor, int offset, double[] array, int arrayOffset, int length) {
		if((offset<0)||(offset+length>length())) throw new IndexOutOfBoundsException();
		for (int i=0; i<length; i++) {
			array[i+arrayOffset]+=factor*unsafeGet(i+offset);
		}
	}
	
	public void addProductToArray(double factor, int offset, AVector other,int otherOffset, double[] array, int arrayOffset, int length) {
		if (other instanceof AArrayVector) {
			addProductToArray(factor,offset,(AArrayVector)other,otherOffset,array,arrayOffset,length);
			return;
		}
		if((offset<0)||(offset+length>length())) throw new IndexOutOfBoundsException();
		for (int i=0; i<length; i++) {
			array[i+arrayOffset]+=factor*unsafeGet(i+offset)*other.get(i+otherOffset);
		}		
	}
	
	public void addProductToArray(double factor, int offset, AArrayVector other,int otherOffset, double[] array, int arrayOffset, int length) {
		if((offset<0)||(offset+length>length())) throw new IndexOutOfBoundsException();
		double[] otherArray=other.getArray();
		otherOffset+=other.getArrayOffset();
		for (int i=0; i<length; i++) {
			array[i+arrayOffset]+=factor*unsafeGet(i+offset)*otherArray[i+otherOffset];
		}		
	}

	public void addProduct(AVector a, int aOffset, AVector b, int bOffset, double factor) {
		int length=length();
		if ((aOffset<0)||(aOffset+length>a.length())) throw new IndexOutOfBoundsException();
		if ((bOffset<0)||(bOffset+length>b.length())) throw new IndexOutOfBoundsException();
		for (int i=0; i<length; i++) {
			addAt(i, (a.unsafeGet(i+aOffset)* b.unsafeGet(i+bOffset)*factor));
		}
	}
	

	@Override
	public void applyOp(IOperator op) {
		if (op instanceof Op) {
			applyOp((Op) op);
		}
		int len=length();
		for (int i=0; i<len; i++) {
			unsafeSet(i,op.apply(unsafeGet(i)));
		}
	}

	@Override
	public void applyOp(Op op) {
		int len=length();
		for (int i=0; i<len; i++) {
			unsafeSet(i,op.apply(unsafeGet(i)));
		}
	}
	
	/**
	 * Adds a value to a specific element of the vector
	 * 
	 * This function does not perform bounds checking, i.e. is an unsafe operation
	 * 
	 * @param i
	 * @param v
	 */
	public void addAt(int i, double v) {
		unsafeSet(i,unsafeGet(i)+v);
	}

	/**
	 * Scales this vector and adds a constant to every element
	 */
	public void scaleAdd(double factor, double constant) {
		scale(factor);
		add(constant);
	}

	@Override
	public void add(double constant) {
		int len=length();
		for (int i=0; i<len; i++) {
			addAt(i,constant);
		}
	}
	
	/**
	 * Returns an exact clone of this vector, i.e. of the same type
	 * @return
	 */
	public abstract AVector exactClone();

	/**
	 * Returns true if this vector exactly matches a double[] array.
	 * @param data
	 * @return
	 */
	public boolean equalsArray(double[] data) {
		int len=length();
		if (len!=data.length) return false;
		for (int i=0; i<len; i++) {
			if (unsafeGet(i)!=data[i]) return false;
		}
		return true;
	}
	
	/**
	 * Returns true if this vector exactly matches the elements in double[] array, starting
	 * from the specified offset
	 * 
	 * @param data
	 * @return
	 */
	public boolean equalsArray(double[] data, int offset) {
		int len=length();
		for (int i=0; i<len; i++) {
			if (unsafeGet(i)!=data[i+offset]) return false;
		}
		return true;
	}

	/**
	 * Set a subrange of this vector from a double array
	 */
	public void setRange(int offset, double[] data, int dataOffset, int length) {
		if ((offset<0)||(offset+length>this.length())) throw new IndexOutOfBoundsException("Offset: "+offset+" , Length: "+length +" on vector with total length "+length());
		for (int i=0; i<length; i++) {
			unsafeSet(offset+i,data[dataOffset+i]);
		}
	}
	
	@Override
	public INDArray broadcast(int... targetShape) {
		int tdims=targetShape.length;
		int len=this.length();
		if (tdims<1) {
			throw new IllegalArgumentException("Can't broadcast to a smaller shape!");
		} else if (tdims==1) {
			if (targetShape[0]!=len) {
				throw new IllegalArgumentException("Can't broadcast to different length: "+targetShape[0]);
			}
			return this;
		} else if (tdims==2) {
			int n=targetShape[0];
			if (len!=targetShape[1]) throw new IllegalArgumentException("Can't broadcast to matrix with different length rows");
			AVector[] vs=new AVector[n];
			for (int i=0; i<n; i++) {vs[i]=this;}
			return Matrixx.createFromVectors(vs);
		} else {
			int n=targetShape[0];
			if (len!=targetShape[tdims-1]) throw new IllegalArgumentException("Can't broadcast to matrix with different length rows");
			INDArray s=broadcast(Arrays.copyOfRange(targetShape, 1, tdims));
			return SliceArray.repeat(s,n);
		}
	}
	
	@Override
	public INDArray broadcastLike(INDArray target) {
		if (target instanceof AMatrix) {
			return broadcastLike((AMatrix)target);
		}
		return broadcast(target.getShape());
	}
	
	public INDArray broadcastLike(AMatrix target) {
		if (length()==target.columnCount()) {
			return BroadcastVectorMatrix.wrap(this, target.rowCount());
		} else {
			throw new IllegalArgumentException(ErrorMessages.incompatibleShapes(this, target));
		}
	}
	
	@Override
	public void validate() {
		super.validate();
	}

}
