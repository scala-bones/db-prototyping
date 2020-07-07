package com.bones.mdwrap.jdbc.integration

import java.sql.Connection

import com.bones.mdwrap.jdbc.{Borrow, LoadCrossReference}
import com.bones.mdwrap.{Column, DatabaseQuery, Deferrability, UpdateDeleteRule}
import org.scalatest.matchers.must.Matchers

class LoadCrossReferenceTest extends IntegrationFixture with Matchers {

  test("load cross reference") { f =>
    val query = DatabaseQuery.everything
    val borrow = new Borrow[Connection](f.con)
    val crossReferenceTables = LoadCrossReference.load(query, borrow)

    val crossReference = crossReferenceTables.get.filter(_.pkColumnTableName == "wrapper_table_a")
    crossReference(0).pkColumnCatalogName mustEqual None
    crossReference(0).pkColumnSchemaName mustEqual Some("public")
    crossReference(0).pkColumnTableName mustEqual "wrapper_table_a"
    crossReference(0).pkColumnName mustEqual "id"
    crossReference(0).foreignCatalogName mustEqual None
    crossReference(0).foreignSchemaName mustEqual Some("public")
    crossReference(0).foreignTableName mustEqual "wrapper_table_b"
    crossReference(0).foreignColumnName mustEqual "table_a_id"
    crossReference(0).keySequence mustEqual 1
    crossReference(0).updateRule mustEqual UpdateDeleteRule.ImportedKeySetDefault
    crossReference(0).deleteRule mustEqual UpdateDeleteRule.ImportedKeySetDefault
    crossReference(0).foreignKeyName mustEqual Some("wrapper_table_b_table_a_id_fkey")
    crossReference(0).primaryKeyName mustEqual Some("wrapper_table_a_id_key")
    crossReference(0).deferrability mustEqual Deferrability.ImportedKeyNotDeferrable
  }

}
