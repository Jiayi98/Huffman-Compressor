
/*  Student information for assignment:
 *
 *  On <OUR> honor, <Jiayi Zhou> and <Yijin Zhao), this programming assignment is <OUR> own work
 *  and <WE> have not provided this code to any other student.
 *
 *  Number of slip days used:
 *
 *  Student 1 Jiayi Zhou
 *  UTEID:jz22393
 *  email address:judyzhou959@utexas.edu
 *  Grader name:Anthony Liu
 *
 *  Student 2
 *  UTEID:yz22566
 *  email address:zyjqcsj@gmail.com
 *
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class SimpleHuffProcessor implements IHuffProcessor {

	private IHuffViewer myViewer;
	private TreeNode tree; // root of the Huffman tree
	private int[] frequency;
	private Map<Integer, String> encodingMap;
	private int format; // to record which kind of format
	private int savedBits; // to record how many bits saved
	private int compressedBits; // total compressed bits

	/**
	 * Compresses input to output, where the same InputStream has previously
	 * been pre-processed via <code>preprocessCompress</code> storing state used
	 * by this call. <br>
	 * pre: <code>preprocessCompress</code> must be called before this method
	 * 
	 * @param in
	 *            is the stream being compressed (NOT a BitInputStream)
	 * @param out
	 *            is bound to a file/stream to which bits are written for the
	 *            compressed file (not a BitOutputStream)
	 * @param force
	 *            if this is true create the output file even if it is larger
	 *            than the input file. If this is false do not create the output
	 *            file if it is larger than the input file.
	 * @return the number of bits written.
	 * @throws IOException
	 *             if an error occurs while reading from the input file or
	 *             writing to the output file.
	 */
	public int compress(InputStream in, OutputStream out, boolean force) throws IOException {
		if (savedBits > 0 || (savedBits < 0 && force)) {// precondition check
			BitOutputStream bos = new BitOutputStream(out);
			bos.writeBits(BITS_PER_INT, MAGIC_NUMBER);// write a sign for
														// HuffCompressed file
			bos.writeBits(BITS_PER_INT, format);// write the currently using
												// format
			// write header
			if (format == IHuffConstants.STORE_COUNTS) {
				for (int k = 0; k < IHuffConstants.ALPH_SIZE; k++) {
					bos.writeBits(BITS_PER_INT, frequency[k]);
				}
			} else if (format == IHuffConstants.STORE_TREE) {
				bos.writeBits(BITS_PER_INT, countTreeSize(tree));
				preOrder(tree, bos);
			}
			BitInputStream bis = new BitInputStream(in);
			actualData(bis, bos);

			// generate encoding result for PSEUDO_EOF constant
			String s = encodingMap.get(PSEUDO_EOF);
			stringToBits(s, bos);
			bos.close();
		}
		return compressedBits;
	}

	// to write string's content to bits
	private void stringToBits(String s, BitOutputStream bos) {
		for (int i = 0; i < s.length(); i++) {
			boolean num = s.charAt(i) == '1';
			if (num)
				bos.writeBits(1, 1);
			else
				bos.writeBits(1, 0);

		}
	}

	// write the actually encoding data
	private void actualData(BitInputStream bis, BitOutputStream bos) throws IOException {
		int previousBit = bis.readBits(BITS_PER_WORD);
		while (previousBit != -1) {
			// to get the encoding result from the map
			String s = encodingMap.get(previousBit);
			stringToBits(s, bos);
			// to read next character
			previousBit = bis.readBits(BITS_PER_WORD);
		}

	}

	// preorderly traverse a tree and write
	private void preOrder(TreeNode n, BitOutputStream bos) {
		if (n.isLeaf()) {
			bos.writeBits(1, 1);// 1 indicates a leaf node
			bos.writeBits(BITS_PER_WORD + 1, n.getValue());
			// using (BITS_PER_WORD + 1) bits because of the pseudo-eof value
		} else {
			bos.writeBits(1, 0);// 0 indicates an internal node
			preOrder(n.getLeft(), bos);
			preOrder(n.getRight(), bos);
		}
	}

	/**
	 * Preprocess data so that compression is possible --- count
	 * characters/create tree/store state so that a subsequent call to compress
	 * will work. The InputStream is <em>not</em> a BitInputStream, so wrap it
	 * int one as needed.
	 * 
	 * @param in
	 *            is the stream which could be subsequently compressed
	 * @param headerFormat
	 *            a constant from IHuffProcessor that determines what kind of
	 *            header to use, standard count format, standard tree format, or
	 *            possibly some format added in the future.
	 * @return number of bits saved by compression or some other measure Note,
	 *         to determine the number of bits saved, the number of bits written
	 *         includes ALL bits that will be written including the magic
	 *         number, the header format number, the header to reproduce the
	 *         tree, AND the actual data.
	 * @throws IOException
	 *             if an error occurs while reading from the input file.
	 */
	public int preprocessCompress(InputStream in, int headerFormat) throws IOException {
		format = headerFormat;// record the format chosen by user
		buildFrequencyArray(in);
		buildHuffmanTree();
		buildMap(tree);

		int countOrigin = getOriginalTotalBits();
		compressedBits = getCompressedTotalBits();
		// add the number of bits of magical number
		compressedBits += IHuffConstants.BITS_PER_INT;
		// add the number of bits of STORAGE_COUNT or STORAGE_TREE constant
		compressedBits += IHuffConstants.BITS_PER_INT;

		// add the number of bits of the header data
		if (headerFormat == STORE_COUNTS) {
			compressedBits += IHuffConstants.ALPH_SIZE * IHuffConstants.BITS_PER_INT;
		} else if (headerFormat == STORE_TREE) {
			compressedBits += countTreeSize(tree) + IHuffConstants.BITS_PER_INT;
		}
		// add number of bits for PSEUDO_EOF
		compressedBits += encodingMap.get(IHuffConstants.PSEUDO_EOF).length();
		savedBits = countOrigin - compressedBits;
		return savedBits;
	}

	// build an array to record frequency of each character
	private void buildFrequencyArray(InputStream in) throws IOException {
		BitInputStream bits = new BitInputStream(in);
		final int[] freq = new int[IHuffConstants.ALPH_SIZE + 1];
		int inbits;
		while ((inbits = bits.readBits(IHuffConstants.BITS_PER_WORD)) != -1) {
			freq[inbits]++;
		}
		freq[IHuffConstants.ALPH_SIZE] = 1; // for pesudo_eof
		frequency = freq;
		bits.close();
	}

	// build a tree according to the frequency array
	private void buildHuffmanTree() {
		tree = null;// initialization
		MyPriorityQueue<TreeNode> q = new MyPriorityQueue<TreeNode>();
		for (int i = 0; i < IHuffConstants.ALPH_SIZE + 1; i++) {
			if (frequency[i] > 0) {
				// current character exists in the file once or more
				q.add(new TreeNode(i, frequency[i]));
			}
		}

		while (q.size() > 1) {
			TreeNode left = q.remove();
			TreeNode right = q.remove();
			TreeNode parent = new TreeNode(left, left.getFrequency() + right.getFrequency(), right);
			q.add(parent);
		}
		tree = q.remove();// return the root
	}

	// build a encoding map according to the tree
	private void buildMap(TreeNode root) {
		Map<Integer, String> m = new HashMap<>();
		recurse(root, "", m);
		encodingMap = m;
	}

	// a recursive method to walk the tree in order to get the encoding result
	// for each character
	private void recurse(TreeNode n, String s, Map<Integer, String> m) {
		if (!n.isLeaf()) {
			recurse(n.getLeft(), s + '0', m);
			recurse(n.getRight(), s + '1', m);
		} else {
			m.put(n.getValue(), s);
		}
	}

	// to get total number of bits of original file
	private int getOriginalTotalBits() {
		int total = 0;
		for (int i = 0; i < IHuffConstants.ALPH_SIZE; i++) {
			total += frequency[i] * IHuffConstants.BITS_PER_WORD;
		}
		return total;
	}

	// to get the total number of bits of the actual data after compressed
	private int getCompressedTotalBits() {
		int count = 0;
		for (int key : encodingMap.keySet()) {
			if (key != IHuffConstants.PSEUDO_EOF) {
				String s = encodingMap.get(key);
				int freq = frequency[key];
				count += freq * s.length();
			}
		}
		return count;
	}

	// to count the size( number of bits ) of the tree
	private int countTreeSize(TreeNode current) {
		if (current.isLeaf()) {
			return 1 + (1 + IHuffConstants.BITS_PER_WORD);
			// 1 bits for a node itself and 9 bits for value
		} else {
			return 1 + countTreeSize(current.getLeft()) + countTreeSize(current.getRight());
		}
	}

	public void setViewer(IHuffViewer viewer) {
		myViewer = viewer;
	}

	/**
	 * Uncompress a previously compressed stream in, writing the uncompressed
	 * bits/data to out.
	 * 
	 * @param in
	 *            is the previously compressed data (not a BitInputStream)
	 * @param out
	 *            is the uncompressed file/stream
	 * @return the number of bits written to the uncompressed file/stream
	 * @throws IOException
	 *             if an error occurs while reading from the input file or
	 *             writing to the output file.
	 */
	public int uncompress(InputStream in, OutputStream out) throws IOException {
		BitInputStream bis = new BitInputStream(in);
		BitOutputStream bos = new BitOutputStream(out);
		// to get magic number and check if this file can be uncompressed
		int magic = bis.readBits(BITS_PER_INT);
		if (magic != MAGIC_NUMBER) {
			myViewer.showError("Error reading compressed file. \n" + "File did not start with the huff magic number.");
			return -1;
		}

		// to read the constant
		format = bis.readBits(BITS_PER_INT);

		// to read the header
		if (format == STORE_COUNTS) {
			rebuildFrequencyArray(bis, bos);// get frequency array
			buildHuffmanTree();// build a tree
		} else if (format == STORE_TREE) {
			bis.readBits(BITS_PER_INT);// skip the size of tree
			tree = rebuildTree(bis, tree); // build a tree
		}

		// walk the tree and write
		int count = walkTree(bis, bos);
		return count;
	}

	// to walk the tree and find the leaf with its value
	private int walkTree(BitInputStream bis, BitOutputStream bos) throws IOException {
		TreeNode temp = tree;
		int count = 0;
		int current = 0;// make it enter the loop
		while (current != -1) {
			current = bis.readBits(1);
			if (current == 1) {
				// 1 indicates right
				temp = temp.getRight();
			} else {
				// 0 indicates right
				temp = temp.getLeft();
			}
			if (temp.isLeaf()) {
				if (temp.getValue() != PSEUDO_EOF) {
					bos.writeBits(BITS_PER_WORD, temp.getValue());
					count += BITS_PER_WORD;
				} else {
					// if current one is PSEUDO_EOF,it will end after this.
					current = -1;
				}
				temp = tree;
			}
		}
		return count;
	}

	// read the header to rebuild a tree
	private TreeNode rebuildTree(BitInputStream bis, TreeNode current) throws IOException {
		if (bis.readBits(1) == 1) {
			// find a leaf
			current = new TreeNode(bis.readBits(BITS_PER_WORD + 1), 0);
			return current;
		} else {
			current = new TreeNode(-1, 0);// -1 indicates that value is null
			current.setLeft(rebuildTree(bis, current.getLeft()));
			current.setRight(rebuildTree(bis, current.getRight()));
			return current;
		}

	}

	// read the header to rebuild a frequency array
	private void rebuildFrequencyArray(BitInputStream bis, BitOutputStream bos) throws IOException {
		frequency = new int[ALPH_SIZE + 1];
		for (int i = 0; i < ALPH_SIZE; i++) {
			int freq = bis.readBits(BITS_PER_INT);
			frequency[i] = freq;
		}
		frequency[ALPH_SIZE] = 1;
	}

	private void showString(String s) {
		if (myViewer != null)
			myViewer.update(s);
	}

	// a nested class for an basic priority queue
	private class MyPriorityQueue<E extends Comparable<? super E>> {
		private LinkedList<E> l;// internal container

		private MyPriorityQueue() {
			l = new LinkedList<E>();
		}

		// add to the correct position of the queue
		private void add(E val) {
			if (l.isEmpty()) {
				l.add(val);
			} else {
				boolean addOK = false;
				// ends the loop immediately after find the right place to
				// insert
				for (int i = 0; i < l.size() && !addOK; i++) {
					int res = l.get(i).compareTo(val);
					if (res > 0) {
						addOK = true;
						l.add(i, val);
					}
				}
				// larger than all elements that are already in the queue
				if (!addOK) {
					l.addLast(val);
				}
			}

		}

		// remove the front of the queue
		private E remove() {
			return l.removeFirst();
		}

		private int size() {
			return l.size();
		}

	}
}
