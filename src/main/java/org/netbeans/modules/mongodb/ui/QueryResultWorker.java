/* 
 * Copyright (C) 2015 Yann D'Isanto
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.netbeans.modules.mongodb.ui;

import org.netbeans.modules.mongodb.MongoExceptionUnwrapper;
import org.netbeans.modules.mongodb.QueryExecutor;
import org.netbeans.modules.mongodb.QueryResult;
import java.text.NumberFormat;
import javax.swing.SwingWorker;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;

/**
 *
 * Executes a createQuery asynchronously, then updates the UI.
 *
 * @author thomaswerner35
 * @author Yann D'Isanto
 */
@Messages({
    "# {0} - execution duration",
    "queryExecSuccess=Executed successfully in {0} s",
    "# {0} - execution duration",
    "queryExecFailure=Execution failed in {0} s",
    "# {0} - records count",
    "queryExecInfo=Showing {0} records that match the query",
    "queryExecInfoSingle=Showing 1 record that matches the query"
})
public abstract class QueryResultWorker extends SwingWorker<QueryResult, Void> implements QueryExecutor {

    private final String name;

    private final int cacheLoadingBlockSize;

    private long durationInMillis = 0;

    private String errorMessage = null;

    private ResultDisplayer resultDisplayer = null;

    private ResultCache cache = ResultCache.EMPTY;
    
    public QueryResultWorker(String name, int cacheLoadingBlockSize) {
        this.name = name;
        this.cacheLoadingBlockSize = cacheLoadingBlockSize;
    }

    protected abstract QueryResult createQuery() throws Exception;

    @Override
    protected QueryResult doInBackground() throws Exception {
        final long start = System.currentTimeMillis();
        try {
            return createQuery();
        } catch (Exception ex) {
            errorMessage = new MongoExceptionUnwrapper(ex).toString();
            return null;
        } finally {
            durationInMillis = System.currentTimeMillis() - start;
        }
    }

    @Override
    protected void done() {
        IOProvider.getDefault().getIO(name, false).closeInputOutput();
        final InputOutput io = IOProvider.getDefault().getIO(name, true);
        try {
            final String duration = NumberFormat.getNumberInstance().format(durationInMillis / 1000.0);
            final QueryResult queryResult = get();
            
            if (null == queryResult) {
                io.getOut().println(Bundle.queryExecFailure(duration));
                if (null != errorMessage) {
                    io.getErr().println("\n" + errorMessage.replaceAll("Source: java.io.StringReader@(.+?); ", ""));
                }
                cache = ResultCache.EMPTY;
            } else {
                cache = new ResultCache(queryResult, cacheLoadingBlockSize);
                io.getOut().println(Bundle.queryExecSuccess(duration));
                if (1 == cache.getObjectsCount()) {
                    io.getOut().println(Bundle.queryExecInfoSingle());
                } else {
                    io.getOut().println(Bundle.queryExecInfo(cache.getObjectsCount()));
                }
            }
            if (resultDisplayer != null) {
                resultDisplayer.updateData(cache, true);
            }
        } catch (Exception ignored) {
            ignored.printStackTrace();
        } finally {
            io.select();
            io.getErr().close();
            io.getOut().close();
        }
    }

    public void setResultDisplayer(ResultDisplayer resultDisplayer) {
        this.resultDisplayer = resultDisplayer;
        if(resultDisplayer != null) {
            resultDisplayer.updateData(cache, true);
        }
    }

}
