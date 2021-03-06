package com.bones.si.ideal

import com.bones.si.jdbc._
import com.bones.si.jdbc.load.DatabaseMetadataCache

/**
  * Responsible for calculating the difference between the 'ideal' and what is in the Database Cache.
  */
object Diff {

  /**
    * Different ways the 'ideal' column can be different than the DB cache.
    */
  trait ColumnDiff

  /** When the 'ideal' data type is different than the column in the DB cache which shares the same column name.*/
  case class ColumnDataTypeDiff(existingDataType: DataType.Value, newDataType: IdealDataType)
      extends ColumnDiff

  /** When the 'ideal' column has a different remark than the DB cache */
  case class ColumnRemarkDiff(existingRemark: Option[String], newRemark: Option[String])
      extends ColumnDiff

  /** When the 'ideal' column has a different nullable value than the DB cache */
  case class ColumnNullableDiff(existingNullable: YesNo.Value, newNullable: YesNo.Value)
      extends ColumnDiff

  /**
   * When the 'ideal' unique constraint is different then the DB cache
   */
  case class UniqueDiff(columnGroup: List[IdealColumn])

  /** When there are missing or extranious Primary Keys between the 'ideal' and the DB Cache
    * @param missing The list of missing PrimaryKeys.
    * @param extraneousKeys The list of primary keys in the DB Cache which are not listed in the 'ideal'.
   **/
  case class PrimaryKeyDiff(missing: List[IdealColumn], extraneousKeys: List[PrimaryKey])

  /**
    * Container of all the Different difference between the 'ideal' and the cache.
    * @param tablesMissing List of missing tables.
    * @param columnsMissing List of Missing column and which tables they should be associated to.
    * @param columnsDifferent List of columns in the cache which have different properties than the 'ideal'.
    * @param primaryKeysMissing List of missing primary Keys.
    * @param primaryKeysExtraneous List of extraneous primary keys.
    * @param missingForeignKeys List of missing Foreign keys.
    */
  case class DiffResult(
    tablesMissing: List[IdealTable],
    columnsMissing: List[(IdealTable, IdealColumn)],
    columnsDifferent: List[(IdealTable, IdealColumn, List[ColumnDiff])],
    primaryKeysMissing: List[(IdealTable, IdealColumn)],
    primaryKeysExtraneous: List[(IdealTable, PrimaryKey)],
    missingForeignKeys: List[IdealForeignKey],
    uniqueDiffs: List[(IdealTable, UniqueDiff)])

  /**
    * Give a database Cache and a Schema Prototype, find the list of changes needed to be made
    * to the Database to be in sync with the Prototype.  This will only find adds and updates to the database.
    * This will not report any structures in the existing database cache that is not part of the Schema Prototype.
    * @param databaseCache The Database Cache for comparison.
    * @param protoSchema The schema prototype -- what structures we want in our database.
    * @return DiffResult with the differences:
    *         * List of tables in the prototype which are not in the cache
    *         * List of columns in the prototype which are not in the cache
    *         * List of columns that are different in the prototype than in the cache
    *         * List of primary keys that are expected in the table cache
    *          * List of primary keys on extra on a table cache
    *         * List of foreign keys in the prototype which are not in the cache
    */
  def findDiff(databaseCache: DatabaseMetadataCache, protoSchema: IdealSchema): DiffResult = {
    val (missingTables, existingTables) =
      missingVsExistingTables(databaseCache, protoSchema.name, protoSchema.tables)
    val missingExistingColumns =
      existingTables.foldLeft(
        (List.empty[(IdealTable, IdealColumn)], List.empty[(IdealTable, IdealColumn, Column)])) {
        (result, table) =>
          {
            val missingExisting = missingVsExistingColumns(databaseCache, table._1, table._2)
            val withTable = missingExisting._1.map(c => (table._1, c))
            result
              .copy(_1 = result._1 ::: withTable)
              .copy(_2 = result._2 ::: missingExisting._2.map(me => (table._1, me._1, me._2)))
          }
      }

    val columnDiff = missingExistingColumns._2.flatMap(c => {
      val indexInfos = databaseCache.indexInfos.filter(i => i.columnName.contains(c._2.name) && i.tableName == c._1.name)
      val diff = compareColumn(c._2, c._3, indexInfos)
      if (diff.isEmpty) None
      else Some((c._1, c._2, diff))
    })

    val primaryKeyDifference = existingTables.map(table => {
      val diff = findPrimaryKeyDifferences(databaseCache, protoSchema.name, table._1, table._2)
      (diff.missing.map((table._1, _)), diff.extraneousKeys.map((table._1, _)))
    })

    val uniqueConstraintDiff = existingTables.flatMap(table => {
      findUniqueConstraintDifferences(databaseCache, protoSchema.name, table._1).map( (table._1, _))
    })

    DiffResult(
      missingTables,
      missingExistingColumns._1,
      columnDiff,
      primaryKeyDifference.flatMap(_._1),
      primaryKeyDifference.flatMap(_._2),
      List.empty[IdealForeignKey],
      uniqueConstraintDiff)

  }

  /**
    * Traverses through the table prototypes and tries to find a table in the cache with same same
    * name and in the same schema.
    * @param databaseCache The cache, to look up tables.
    * @param schemaName The name of the prototype schema
    * @param tables The list of prototype tables we are looking up in the cache
    * @return Tuple2 where _1 is the list of missing tables and _2 is the
    *         list of pair of the prototype table and the cached table.
    */
  def missingVsExistingTables(
    databaseCache: DatabaseMetadataCache,
    schemaName: String,
    tables: List[IdealTable]): (List[IdealTable], List[(IdealTable, Table)]) = {
    tables.foldRight((List.empty[IdealTable], List.empty[(IdealTable, Table)])) {
      (subTable, result) =>
        {
          databaseCache.findTableByName(schemaName, subTable.name) match {
            case Some(table) => result.copy(_2 = (subTable, table) :: result._2)
            case None        => result.copy(_1 = subTable :: result._1)
          }
        }
    }
  }

  /**
    * Compare the two tables and try to find a cached column which matches each of the columns in the protoype table.
    * @param databaseCache The cache used for lookup.
    * @param table The table prototype, what we want the table to look like
    * @param diffTable The cached table
    * @return Pair of List where _1 is the List of columns not found in the cache and _2 is the pair of matching prototype/existing columns
    */
  def missingVsExistingColumns(
    databaseCache: DatabaseMetadataCache,
    table: IdealTable,
    diffTable: Table): (List[IdealColumn], List[(IdealColumn, Column)]) = {
    table.allColumns.foldLeft((List.empty[IdealColumn], List.empty[(IdealColumn, Column)])) {
      (result, protoColumn) =>
        {
          databaseCache.findColumnByName(diffTable, protoColumn.name) match {
            case None    => result.copy(_1 = protoColumn :: result._1)
            case Some(c) => result.copy(_2 = (protoColumn, c) :: result._2)
          }
        }
    }
  }

  object PrimaryKeyDiff {
    def withExtraneous(extraneous: List[PrimaryKey]): PrimaryKeyDiff =
      PrimaryKeyDiff(List.empty, extraneous)
  }

  def findUniqueConstraintDifferences(databaseCache: DatabaseMetadataCache, schemaName: String, table: IdealTable): List[UniqueDiff] = {
    val tableIndexes = databaseCache.indexInfos.filter(ii => ii.tableName == table.name && ii.tableSchema.contains(schemaName))
    val grouped = tableIndexes.groupBy(_.indexName)
    val missingConstraint = table.uniqueConstraints.filterNot(uc => {
      val columnNames = uc.uniqueGroup.map(_.name).toSet
      //see if there is an unique constraint which contains the column group and only the column group
      grouped.values.exists(_.flatMap(_.columnName).toSet == columnNames)
    })
    missingConstraint.map(uc => UniqueDiff(uc.uniqueGroup))
  }

  def findPrimaryKeyDifferences(
    databaseCache: DatabaseMetadataCache,
    schemaName: String,
    table: IdealTable,
    diffTable: Table): PrimaryKeyDiff = {
    val tablePks = databaseCache.primaryKeys.filter(pk =>
      pk.schemaName.contains(schemaName) && pk.tableName == table.name)
    //The list of Differences and remaining PrimaryKeys in the cache which
    // are not currently matched up with a ProtoColumn
    table.primaryKeyColumns.foldLeft(PrimaryKeyDiff.withExtraneous(tablePks)) {
      (result, nextColumn) =>
        {
          val diff = result
          val (matchingPks, remainingPks) =
            diff.extraneousKeys.partition(pk => pk.columnName == nextColumn.name)
          matchingPks match {
            case _ :: Nil =>
              diff.copy(extraneousKeys = remainingPks)
            case _ :: xs =>
              //column matches more than one PK.  This shouldn't happen, so we'll use one and keep the rest in remaining
              diff.copy(extraneousKeys = xs ::: remainingPks)
            case _ =>
              diff.copy(missing = nextColumn :: diff.missing)
          }
        }
    }
  }

  /**
    * Compares two columns for differences, currently including data type, remarks or nullable.
    * @param column The column prototype
    * @param diffColumn The cached column for comparison
    * @return List of differences
    */
  def compareColumn(column: IdealColumn, diffColumn: Column, indexInfos: List[IndexInfo]): List[ColumnDiff] = {
    val dt =
      if (!isEquivalent(column.dataType, diffColumn.dataType, diffColumn))
        List(ColumnDataTypeDiff(diffColumn.dataType, column.dataType))
      else List.empty
    val rm =
      if (column.remark != diffColumn.remarks)
        List(ColumnRemarkDiff(diffColumn.remarks, column.remark))
      else List.empty
    val nl = {
      if (diffColumn.isNullable == YesNo.Unknown ||
          (column.nullable && diffColumn.isNullable == YesNo.No) ||
          (!column.nullable && diffColumn.isNullable == YesNo.Yes))
        List(ColumnNullableDiff(diffColumn.isNullable, YesNo.fromBoolean(column.nullable)))
      else List.empty
    }

    dt ::: rm ::: nl ::: Nil
  }

  /**
   * Check the ideal type of the array compared to the metadata.  Most of the
   * time we can ensure the sizes match (such as varchar(50) == string bounded to 50 characters)
   * but in a few cases we can not compare the upper bound.  In this case we say the
   * two are equivalent if the metadata size is greater than the ideal size.
   * TODO: This is most certainly PostgreSQL specific.
   * @param dataType The ideal type of the data in the array
   * @param column The jdbc metadata column
   * @return If the column is equivalent to the data type.
   */
  protected def checkArray(dataType: IdealDataType, column: Column): Boolean = {
    (dataType, column.typeName) match {
      case (BinaryType(Some(size)), "_bit") if size == 1 => column.columnSize == 1
      case (SmallIntType, "_int2") => true
      case (StringType(size, _), "_text") => size.forall(column.columnSize >= _)
      case (StringType(size, _), "_varchar") => size.contains(size)
      case (IntegerType(_), "_int4") => true
      case (LongType(_), "_int8") => true
      case (RealType, "_float4") => true
      case (DoubleType, "_float8") => true
      case (NumericType(precision,scale), "_numeric") =>
        precision == column.columnSize && column.decimalDigits.contains(scale)
      case (DateType, "_date") => true
      case (TimestampType(tz), "_timestamp") if !tz => true
      case (TimestampType(tz), "_timestampz") if tz => true
      case (TimeType(tz), "_time") if !tz => true
      case (TimeType(tz), "_timez") if tz => true
      case (FixedLengthBinaryType(size), "_varbit") => column.columnSize > size // Postgres metadata columnSize doesn't reflect the bounded size
      case (BinaryType(size), "_bytea") => size.forall(column.columnSize > _)
      case (BooleanType, "_bool") => true
      case (FixedLengthCharacterType(size, _), "_bpchar") =>column.columnSize == size
      case _ => false
    }
  }

  /**
    * Goal is to determine if the JDBC type satisfies the specified DataType for the ProtoColumn
    * @param idealDataType What the data type should be.
    * @param dataType What the data type is in the cache.
    * @param column The column in the cache which contains the data type.
    * @return
    */
  def isEquivalent(
    idealDataType: IdealDataType,
    dataType: DataType.Value,
    column: Column): Boolean = {
    (idealDataType, dataType) match {
      case (ArrayType(arrayOf), DataType.Array) => checkArray(arrayOf, column)
      case (BinaryType(size), DataType.Bit) if size.contains(1) => true
      case (SmallIntType, DataType.TinyInt)                     => true
      case (SmallIntType, DataType.SmallInt)                    => true
      case (IntegerType(_), DataType.Integer)                   => true
      case (LongType(_), DataType.BigInt)                       => true
      case (RealType, DataType.Float)                           => true
      case (RealType, DataType.Real)                            => true
      case (DoubleType, DataType.Double)                        => true
      case (NumericType(p, s), DataType.Numeric) =>
        column.columnSize == p && column.decimalDigits.contains(s)
      case (NumericType(p, s), DataType.Decimal) =>
        column.columnSize == p && column.decimalDigits.contains(s)
      case (StringType(sz, _), DataType.VarChar) =>
        sz match {
          case Some(i) if i > 255 => column.columnSize >= i
          case Some(i)            => column.columnSize == i
          case None               => column.typeName != "varchar" //eg, postgres uses "text" for unlimited size
        }
      case (StringType(sz, charset), DataType.LongVarChar) =>
        sz match {
          case Some(i) if i > 255 => column.columnSize >= i
          case Some(i)            => column.columnSize == i
          case None               => true
        }
      case (DateType, DataType.Date)                      => true
      case (TimeType(tz), DataType.Time) if !tz           => true
      case (TimestampType(tz), DataType.Timestamp) if !tz => true
      case (BinaryType(size), DataType.VarBinary) =>
        size.forall(column.columnSize > _)
      case (FixedLengthBinaryType(size), DataType.Binary) =>
        column.columnSize == size
      case (BinaryType(size), DataType.Blob) =>
        size.forall(column.columnSize >= _)
      case (StringType(size, _), DataType.Clob) =>
        size.forall(column.columnSize >= _)
      case (BooleanType, DataType.Boolean)                           => true
      case (FixedLengthCharacterType(size, charset), DataType.NChar) => true
      case (StringType(size, _), DataType.NVarChar) =>
        size.forall(column.columnSize >= _)
      case (StringType(size, _), DataType.LongNVarChar) =>
        size.forall(column.columnSize > _)
      case (StringType(size, _), DataType.NClob) =>
        size.forall(column.columnSize >= _)
      case (TimeType(tz), DataType.TimeWithTimeZone) if tz           => true
      case (TimestampType(tz), DataType.TimestampWithTimeZone) if tz => true
      case _                                                         => false
    }

  }

}
