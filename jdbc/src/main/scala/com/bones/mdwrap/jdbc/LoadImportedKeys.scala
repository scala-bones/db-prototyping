package com.bones.mdwrap.jdbc

import java.sql.{Connection, ResultSet}

import com.bones.mdwrap.jdbc.Retrieve.databaseQueryToHierarchyQuery
import com.bones.mdwrap.{DatabaseQuery, Deferrability, ImportedKeys, UpdateDeleteRule}

object LoadImportedKeys extends DefaultLoader[ImportedKeys] {

  override protected def loadFromQuery(
    databaseQuery: DatabaseQuery,
    con: Connection): LazyList[ResultSet] =
    databaseQueryToHierarchyQuery(databaseQuery)
      .to(LazyList)
      .map(param =>
        con.getMetaData.getImportedKeys(param._1.orNull, param._2.orNull, param._3.orNull))

  protected def extractRow(rs: ResultSet): ImportedKeys = {
    val updateRuleId = rs.getInt("UPDATE_RULE")
    val updateRule = UpdateDeleteRule
      .findById(updateRuleId)
      .getOrElse(
        throw new IllegalStateException(s"could not find UpdateDeleteRule by id: ${updateRuleId}"))
    val deleteRuleId = rs.getInt("DELETE_RULE")
    val deleteRule = UpdateDeleteRule
      .findById(updateRuleId)
      .getOrElse(
        throw new IllegalStateException(s"could not find UpdateDeleteRule by id: ${deleteRuleId}"))
    val deferrabilityId = rs.getInt("DEFERRABILITY")
    val deferrability = Deferrability
      .findById(deferrabilityId)
      .getOrElse(
        throw new IllegalStateException(s"could not find Deferrability by id: ${deferrabilityId}"))

    ImportedKeys(
      Option(rs.getString("PKTABLE_CAT")),
      Option(rs.getString("PKTABLE_SCHEM")),
      req(rs.getString("PKTABLE_NAME")),
      req(rs.getString("PKCOLUMN_NAME")),
      Option(rs.getString("FKTABLE_CAT")),
      Option(rs.getString("FKTABLE_SCHEM")),
      req(rs.getString("FKTABLE_NAME")),
      req(rs.getString("FKCOLUMN_NAME")),
      req(rs.getShort("KEY_SEQ")),
      updateRule,
      deleteRule,
      Option(rs.getString("FK_NAME")),
      Option(rs.getString("PK_NAME")),
      deferrability
    )

  }

}
