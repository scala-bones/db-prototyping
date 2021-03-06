package com.bones.si.jdbc.load

import java.sql.{Connection, ResultSet}

import com.bones.si.jdbc.{TablePrivilege, YesNo}

object LoadTablePrivilege extends DefaultLoader[TablePrivilege] {
  override protected def loadFromQuery(databaseQuery: DatabaseQuery, con: Connection): Stream[ResultSet] =
    Retrieve.databaseQueryToHierarchyQuery(databaseQuery).toStream.map(param =>
      con.getMetaData.getTablePrivileges(param._1.orNull, param._2.orNull, param._3.orNull)
    )

  override protected def extractRow(rs: ResultSet): TablePrivilege = {
    val isGrantableStr = Option(rs.getString("IS_GRANTABLE"))
    val isGrantable = YesNo.findByOptionalString(isGrantableStr).getOrElse(throw new MissingDataException((s"could not determine gratable from $isGrantableStr")))
    TablePrivilege(
      Option(rs.getString("TABLE_CAT")),
      Option(rs.getString("TABLE_SCHEM")),
      req(rs.getString("TABLE_NAME")),
      Option(rs.getString("GRANTOR")),
      req(rs.getString("GRANTEE")),
      req(rs.getString("PRIVILEGE")),
      isGrantable
    )
  }
}
