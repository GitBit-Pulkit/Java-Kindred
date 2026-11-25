package com.kindred.model;

/**
 * Any model that can be written as one row of a CSV file implements this.
 *
 * CsvExporter accepts a List<? extends CsvExportable>, so it can export
 * Users, Skills, Sessions or Transactions with ONE method.
 */
// SYLLABUS: Generics - interface used as an upper bound (List<? extends CsvExportable>)
public interface CsvExportable {

    /** Values of one CSV row, in the same order as the model's CSV_HEADER. */
    String[] csvValues();
}
