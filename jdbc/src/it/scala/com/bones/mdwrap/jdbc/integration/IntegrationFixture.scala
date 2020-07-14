package com.bones.mdwrap.jdbc.integration

import java.sql.Connection

import org.postgresql.ds.PGSimpleDataSource
import org.scalatest.funsuite.FixtureAnyFunSuite
import org.scalatest.{FixtureTestSuite, Outcome}

abstract class IntegrationFixture extends FixtureAnyFunSuite {

  case class FixtureParam(con: Connection)

  override def withFixture(test: OneArgTest): Outcome = {

    val ds = new PGSimpleDataSource() ;
    ds.setURL("jdbc:postgresql://localhost/postgres?user=travis&password=secret")
    val con = ds.getConnection
    dropTables(con)
    createStructures(con)
    val theFixture = FixtureParam(con)
    try {
      withFixture(test.toNoArgTest(theFixture))
    } finally {
      dropTables(ds.getConnection)
    }
  }

  private def createFunction(con: Connection): Unit = {
    val sql =
      """
        |CREATE OR REPLACE FUNCTION db_test_add(i1 integer, i2 integer) RETURNS integer
        |    AS 'select i1 + i2;'
        |    LANGUAGE SQL
        |    IMMUTABLE
        |    RETURNS NULL ON NULL INPUT;
        |""".stripMargin

    val st1 = con.createStatement()
    st1.execute(sql)
    st1.close()

  }

  private def createProcedure(con: Connection): Unit = {
    val sql =
      """
        |CREATE OR REPLACE PROCEDURE db_test_insert_data(a integer, b integer)
        |LANGUAGE SQL
        |AS $$
        |INSERT INTO tbl VALUES (a);
        |INSERT INTO tbl VALUES (b);
        |$$;
        |""".stripMargin
  }

  def createStructures(con: Connection): Unit = {
    val table1 =
      """
        |create table wrapper_table_a (
        | id SERIAL UNIQUE,
        | big_id BIGSERIAL UNIQUE,
        | bit_col BIT NOT NULL,
        | bit_varying_col BIT(5),
        | name TEXT,
        | char_col CHAR,
        | char_varying_col VARCHAR(255),
        | date_col DATE,
        | double_col DOUBLE PRECISION,
        | integer_col INTEGER,
        | numeric_col NUMERIC(9,3),
        | real_col REAL,
        | small_int_col SMALLINT,
        | text_col TEXT,
        | time_col TIME,
        | time_with_timezone_col TIME WITH TIME ZONE,
        | timestamp_col TIMESTAMP,
        | timestamp_with_timezone_col TIMESTAMP WITH TIME ZONE,
        | xml_col XML,
        | PRIMARY KEY(id, big_id) )
        |
        |""".stripMargin

    val table2 =
      """
        |create table wrapper_table_b (
        |  id SERIAL UNIQUE,
        |  table_a_id INT,
        |  PRIMARY KEY (id),
        |  FOREIGN KEY (table_a_id) REFERENCES wrapper_table_a (id)
        |)
        |""".stripMargin

    val st1 = con.createStatement()
    st1.execute(table1)
    st1.close()

    val st2 = con.createStatement()
    st2.execute(table2)
    st2.close()

    createFunction(con)
    createProcedure(con)

    con.close()


  }

  def dropTables(con: Connection): Unit = {
    val table2 = "drop table if exists wrapper_table_b"
    val st2 = con.createStatement()
    st2.execute(table2)
    st2.close()

    val table1 = "drop table if exists wrapper_table_a cascade"
    val st1 = con.createStatement()
    st1.execute(table1)
    st1.close()

    val sf = con.createStatement()
    sf.execute("drop function if exists db_test_add;")
    sf.close()

    val sp = con.createStatement()
    sp.execute("drop procedure if exists db_test_insert_data")
    sp.close()

    con.close()
  }

}
