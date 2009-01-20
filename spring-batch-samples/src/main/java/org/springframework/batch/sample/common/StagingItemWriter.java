/*
 * Copyright 2006-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.batch.sample.common;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.lang.SerializationUtils;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.support.JdbcDaoSupport;
import org.springframework.jdbc.support.incrementer.DataFieldMaxValueIncrementer;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Database {@link ItemWriter} implementing the process indicator pattern.
 */
public class StagingItemWriter<T> extends JdbcDaoSupport implements StepExecutionListener, ItemWriter<T> {

	public static final String NEW = "N";

	public static final String DONE = "Y";

	public static final Object WORKING = "W";

	private DataFieldMaxValueIncrementer incrementer;

	private StepExecution stepExecution;

	/**
	 * Check mandatory properties.
	 * 
	 * @see org.springframework.dao.support.DaoSupport#initDao()
	 */
	protected void initDao() throws Exception {
		super.initDao();
		Assert.notNull(incrementer, "DataFieldMaxValueIncrementer is required - set the incrementer property in the "
				+ ClassUtils.getShortName(StagingItemWriter.class));
	}

	/**
	 * Setter for the key generator for the staging table.
	 * 
	 * @param incrementer the {@link DataFieldMaxValueIncrementer} to set
	 */
	public void setIncrementer(DataFieldMaxValueIncrementer incrementer) {
		this.incrementer = incrementer;
	}

	/**
	 * Serialize the item to the staging table, and add a NEW processed flag.
	 * 
	 * @see ItemWriter#write(java.util.List)
	 */
	public void write(final List<? extends T> items) {

		final ListIterator<? extends T> itemIterator = items.listIterator();
		getJdbcTemplate().batchUpdate("INSERT into BATCH_STAGING (ID, JOB_ID, VALUE, PROCESSED) values (?,?,?,?)",
				new BatchPreparedStatementSetter() {

					public int getBatchSize() {
						return items.size();
					}

					public void setValues(PreparedStatement ps, int i) throws SQLException {

						long id = incrementer.nextLongValue();
						long jobId = stepExecution.getJobExecution().getJobId();

						Assert.state(itemIterator.nextIndex() == i,
								"Item ordering must be preserved in batch sql update");

						byte[] blob = SerializationUtils.serialize((Serializable) itemIterator.next());

						ps.setLong(1, id);
						ps.setLong(2, jobId);
						ps.setBytes(3, blob);
						ps.setString(4, NEW);
					}
				});

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.batch.core.domain.StepListener#afterStep(StepExecution
	 * )
	 */
	public ExitStatus afterStep(StepExecution stepExecution) {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @seeorg.springframework.batch.core.domain.StepListener#beforeStep(org.
	 * springframework.batch.core.domain.StepExecution)
	 */
	public void beforeStep(StepExecution stepExecution) {
		this.stepExecution = stepExecution;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.batch.core.domain.StepListener#onErrorInStep(java
	 * .lang.Throwable)
	 */
	public ExitStatus onErrorInStep(StepExecution stepExecution, Throwable e) {
		return null;
	}

}