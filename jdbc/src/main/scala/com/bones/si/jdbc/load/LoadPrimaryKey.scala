package com.bones.si.jdbc.load

import java.sql.{Connection, ResultSet}

import com.bones.si.jdbc.PrimaryKey

object LoadPrimaryKey extends DefaultLoader[PrimaryKey] {


  override protected def loadFromQuery(databaseQuery: DatabaseQuery, con: Connection): Stream[ResultSet] =
    Retrieve.databaseQueryToHierarchyQuery(databaseQuery).toStream.map(param =>
      con.getMetaData.getPrimaryKeys(param._1.orNull, param._2.orNull, param._3.orNull)
    )

  override protected def extractRow(rs: ResultSet): PrimaryKey = {
    PrimaryKey(
      Option(rs.getString("TABLE_CAT")),
      Option(rs.getString("TABLE_SCHEM")),
      req(rs.getString("TABLE_NAME")),
      req(rs.getString("COLUMN_NAME")),
      req(rs.getShort("KEY_SEQ")),
      Option(rs.getString("PK_NAME"))
    )
  }

}
