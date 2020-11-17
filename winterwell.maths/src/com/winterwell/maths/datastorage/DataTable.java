package com.winterwell.maths.datastorage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.winterwell.utils.Key;
import com.winterwell.utils.Printer;
import com.winterwell.utils.TodoException;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.containers.Table;
import com.winterwell.utils.io.CSVReader;
import com.winterwell.utils.io.CSVWriter;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.io.ISerialize;
import com.winterwell.utils.log.KErrorPolicy;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.web.IHasHtml;
import com.winterwell.web.HtmlTable;

/**
 * A simple data table, for when a List or HashMap isn't quite good enough. Has
 * an index on the first column. It is designed to be close to a drop-in
 * replacement for HashMap and List.
 * 
 * Probably thread safe.
 * 
 * @author daniel
 * @param <C1>
 *            type of the 1st column
 * @testedby  DataTableTest}
 */
public class DataTable<C1> extends Table<C1, Object[]> implements IHasHtml {
	static KErrorPolicy exceptionPolicy = KErrorPolicy.THROW_EXCEPTION;

	private static final long serialVersionUID = 1L;

	/**
	 * Hokey method to make it slightly nicer to write data-processing
	 * "scripts". Since it's shared across all DataTables, avoid like the plague
	 * in real code.
	 * 
	 * @param policy
	 */
	@Deprecated
	public static void setExceptionPolicy(KErrorPolicy policy) {
		exceptionPolicy = policy;
	}

	private final ISerialize[] rowSerialisers;

	@Override
	protected final C1 getRowKey(Object[] row) {
		return (C1) row[0];
	}
	
	/**
	 * Loads the csv file, if given.
	 * 
	 * @param in
	 *            Can be null. Will be closed after reading.
	 * @param rowSerialisers
	 *            Can be null. Can contain nulls (which will result in the
	 *            Strings being used as-is). Can be shorter than the full row
	 *            length.
	 */
	public DataTable(CSVReader in, ISerialize... rowSerialisers) {
		super(Object[].class);
		this.rowSerialisers = rowSerialisers;
		if (in == null)
			return;
		load(in);
		FileUtils.close(in);
	}

	/**
	 * Create an empty DataTable with no serialisers.
	 */
	public DataTable() {
		super(Object[].class);
		this.rowSerialisers = null;
	}
	
	public DataTable(ISerialize... rowSerialisers) {
		this(Collections.EMPTY_LIST, rowSerialisers);
	}

	public DataTable(List<Object[]> rows, ISerialize... rowSerialisers) {
		super(Object[].class);
		this.rowSerialisers = rowSerialisers;
		for (Object[] row : rows) {
			add(row);
		}
	}

	/**
	 * Add a row. The 0th item will be used as the index for
	 * {@link #get(Object)}.
	 * 
	 * @param row
	 *            All rows must be the same length.
	 */
	public synchronized void add(Object... row) {
		// Guard against an easy mistake (which could technically be legit, but
		// probably is a bug)
		if (row.length == 1 && row[0] != null) {
			if (row[0].getClass().isArray()) {
				Log.w("data", "converting " + Printer.toString(row[0]) + " to Object[]");
				Object innards = row[0];
				List<Object> list = Containers.asList(innards);
				row = list.toArray();
			} else if (row[0] instanceof List) {
				row = ((List)row[0]).toArray();
			}
		}
		super.add(row);
	}

	public void add(List row) {
		add(row.toArray());
	}
	
	public void addColumn(Collection col) {
		if (col.size() != size()) {
			// hm! a short / long column
			Log.v("DataTable", "Hm: column size mismatch: table:"+size()+" col: "+col.size());
		}
		int w = getWidth();
		int wPlus = w+1;
		int i=0;
		for (Object vi : col) {
			Object[] row2;
			if (i < size()) {
				row2 = Arrays.copyOf(getLowLevel().get(i), wPlus);
				getLowLevel().set(i, row2);
			} else {
				row2 = new Object[wPlus];
				add(row2);
			}
			row2[w] = vi;			
			i++;
		}
	}

	/**
	 * 
	 * @param i
	 * @return A fresh object - edits to the List will not alter this DataTable 
	 */
	public List getColumn(int i) {
		ArrayList col = new ArrayList(size());
		for (Object[] row : this) {
			col.add(row[i]);
		}
		return col;
	}


	/**
	 * Status: sketch!
	 * 
	 * Uses the {@link #rowSerialisers} -- provided they're Keys or AFields --
	 * to create a map-view of the row.
	 * 
	 * @param row
	 */
	public Map getRowMap(int row) {
		Object[] arr = getRow(row);
		ArrayMap map = new ArrayMap();
		for (int i = 0; i < rowSerialisers.length; i++) {
			ISerialize ser = rowSerialisers[i];
			if (ser instanceof Key) {
				String name = ((Key) ser).getName();
				map.put(name, arr[i]);
			} else {
				// skip
			}
		}
		return map;
	}

	/**
	 * 
	 * @return the number of columns in this table. This is a convenience for getting the length of the 1st row.
	 * 0 if there are no rows.
	 */
	public int getWidth() {
		return size()==0? 0 : getRow(0).length;
	}


	public void load(CSVReader in) {
		for (String[] row : in) {
			if (row.length == 0) {
				continue;
			}
			Object[] row2 = new Object[row.length];
			// convert?
			for (int i = 0; i < row.length; i++) {
				// See note in save
				if (row[i].equals("")) {
					row2[i] = null;
					continue;
				}
				try {
					ISerialize rs = rowSerialisers != null
							&& rowSerialisers.length > i ? rowSerialisers[i]
							: null;
					Object x = rs == null ? row[i] : rowSerialisers[i]
							.fromString(row[i]);
					row2[i] = x;
				} catch (Exception e) {
					switch (exceptionPolicy) {
					case THROW_EXCEPTION:
						throw Utils.runtime(e);
					case DIE: {
						Log.report("Problem in CSV load, and DIE requested. Exiting immediately.");
						System.exit(1);
					}
					case REPORT:
						Log.report(e + " from " + row[i]);
					case IGNORE:
						break;
					case RETURN_NULL:
						row2[i] = null;
						break;
					case DELETE_CAUSE:
						throw new TodoException(e.getMessage());
					}
				}
			}
			// row done
			add(row2);
		}
	}

	/**
	 * @param out
	 *            This will be closed at the end.
	 */
	public void save(CSVWriter out) {
		try {
			for (Object[] objs : this) {
				String[] strings = new String[objs.length];
				// convert?
				for (int i = 0; i < objs.length; i++) {
					// This has the effect of writing null strings for null
					// values
					if (objs[i] == null) {
						strings[i] = null;
						continue;
					}
					ISerialize rs = rowSerialisers != null
							&& rowSerialisers.length > i ? rowSerialisers[i]
							: null;
					String s = rs == null ? String.valueOf(objs[i])
							: rowSerialisers[i].toString(objs[i]);
					strings[i] = s;
				}
				out.write(strings);
			}
			out.close();
		} catch (Exception e) {
			throw Utils.runtime(e);
		}
	}

	@Override
	public void appendHtml(StringBuilder sb) {
		sb.append(toHTMLString());
	}

	@Override
	public String toHTMLString() {
		HtmlTable tbl = new HtmlTable(getWidth());
		tbl.setCSSClass("table"); // let's default to Bootstrap
		for (Object[] row : rows) {
			tbl.addRow(row);
		}
		return tbl.toHTML();
	}


}
