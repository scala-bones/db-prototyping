package com.bones.mdwrap.jdbc.integration

import com.bones.mdwrap.{DatabaseQuery, YesNo}
import com.bones.mdwrap.jdbc.LoadTablePrivilege
import org.scalatest.matchers.must.Matchers

class LoadTablePrivilegeTest extends IntegrationFixture with Matchers {

  test("load table privilege") { f =>
    val query = DatabaseQuery.everything
    val priv = LoadTablePrivilege.load(query, f.con)

    priv.length must be > 0

    val publicTables = priv.filter(_.schemaName.contains("public"))

    val table1Insert = publicTables.find(pr => pr.name == "table1" && pr.privilege == "INSERT").get
    table1Insert.catalogName mustEqual None
    table1Insert.grantee mustEqual "travis"
    table1Insert.grantor mustEqual Some("travis")
    table1Insert.isGrantable mustEqual YesNo.Yes
  }

}
