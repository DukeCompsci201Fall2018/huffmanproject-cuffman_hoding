import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	
	
	private int[] read_for_counts(BitInputStream in) {
	    int[] ret = new int[ALPH_SIZE + 1];
	    while(true) {
	        int val = in.readBits(BITS_PER_WORD);
	        if (val == -1) break;
	        ret[val] += 1;
	    }
	    return ret;
	}
	
	private HuffNode make_tree_from_count(int[] counts) {
	    
	    PriorityQueue<HuffNode> pq = new PriorityQueue<>();
	    for(int i = 0; i < ALPH_SIZE + 1; i++) {
	        if (counts[i] != 0) pq.add(new HuffNode(i, counts[i], null, null));
	    }
	    while (pq.size() > 1) {
	        HuffNode left = pq.remove();
	        HuffNode right = pq.remove();
	        HuffNode t = new HuffNode(0, left.myWeight + right.myWeight, left, right);
	        pq.add(t);
	    }
	    HuffNode root = pq.remove();
	    
	    return root;
	}
	
	   
    private String[] make_codings_from_tree(HuffNode root) {
        String[] ret = new String[ALPH_SIZE + 1];
        coding(root, "", ret);
        return ret;
    }
    
    private void coding(HuffNode node, String track, String[] ret) {
        if (node.myValue != 0) ret[node.myValue] = track;
        else {
            coding(node.myLeft, track + "0", ret);
            coding(node.myRight, track + "1", ret);
        }
    }
    
    private void write_compressed_bits(String[] codings, BitInputStream in, BitOutputStream out) {
        
        while(true) {
            int letter = in.readBits(BITS_PER_WORD);
            if (letter == -1) break;
            String encoded = codings[letter];
            out.writeBits(encoded.length(), Integer.parseInt(encoded, 2));
        }
        out.writeBits(codings[PSEUDO_EOF].length(), Integer.parseInt(codings[PSEUDO_EOF], 2));
        
    }
    
    private void write_header(HuffNode root, BitOutputStream out) {
        if (root.myValue == 0) {
            out.writeBits(1, 0);
            write_header(root.myLeft, out);
            write_header(root.myRight, out);
        }
        else {
            out.writeBits(1, 1);
            out.writeBits(BITS_PER_WORD, root.myValue);
        }
    }
    
	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
	    
	    int[] counts = read_for_counts(in);
	    HuffNode root = make_tree_from_count(counts);
	    String[] codings = make_codings_from_tree(root);
	    
	    out.writeBits(BITS_PER_INT,  HUFF_TREE);
	    write_header(root, out);
	    
	    in.reset();
	    write_compressed_bits(codings, in, out);
	    out.close();
	}


    /**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	
	private HuffNode read_trees(BitInputStream in) {
	    int bit_read = in.readBits(1);
	    switch(bit_read) {
    	    case -1:
    	        throw new HuffException("illegal -1");
    	    case 0:
    	        HuffNode l = read_trees(in);
    	        HuffNode r = read_trees(in);
    	        return new HuffNode(0, 0, l, r);
    	    default:
    	        return new HuffNode(in.readBits(BITS_PER_WORD + 1), 0, null, null);
	    }
	}
	
	private void read_compressed_bits(HuffNode root, BitInputStream in, BitOutputStream out) {
	    HuffNode tmp = root;
	    while(true) {
	        int bit = in.readBits(1);
	        if (bit == -1) throw new HuffException("bad input, no PSEUDO_EOF");
	        else {
	            if (bit == 0) tmp = tmp.myLeft;
	            else tmp = tmp.myRight;
	            if (tmp.myValue != 0) {
	                if (tmp.myValue == PSEUDO_EOF) break;
	                else {
	                    out.writeBits(BITS_PER_INT, tmp.myValue);
	                    tmp = root;
	                }
	            }
	        }
	    }
	}
	
	public void decompress(BitInputStream in, BitOutputStream out){
	    
	    // Check the standard header
	    int bits = in.readBits(BITS_PER_INT);
	    if (bits != HUFF_TREE) throw new HuffException("illegal header starts with"+  bits);
	    HuffNode root = read_trees(in);
	    read_compressed_bits(root, in, out);
		out.close();
	}
}