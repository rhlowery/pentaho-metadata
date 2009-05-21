package org.pentaho.metadata.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.pentaho.di.core.Props;
import org.pentaho.metadata.model.Category;
import org.pentaho.metadata.model.Domain;
import org.pentaho.metadata.model.LogicalColumn;
import org.pentaho.metadata.model.LogicalModel;
import org.pentaho.metadata.model.LogicalTable;
import org.pentaho.metadata.model.SqlPhysicalColumn;
import org.pentaho.metadata.model.SqlPhysicalModel;
import org.pentaho.metadata.model.SqlPhysicalTable;
import org.pentaho.metadata.model.concept.types.DataType;
import org.pentaho.metadata.model.concept.types.LocaleType;
import org.pentaho.metadata.model.concept.types.LocalizedString;
import org.pentaho.metadata.model.concept.types.TargetTableType;
import org.pentaho.pms.messages.util.LocaleHelper;
import org.pentaho.pms.util.Settings;

public class SQLModelGenerator {
  String modelName;
  Connection connection;
  String query;
  String connectionName;
  
  public SQLModelGenerator() {
    super();
    if(!Props.isInitialized()) {
      Props.init(Props.TYPE_PROPERTIES_EMPTY);
    }
  }

  public SQLModelGenerator(String modelName, String connectionName, Connection connection, String query) {
    if(!Props.isInitialized()) {
      Props.init(Props.TYPE_PROPERTIES_EMPTY);
    }
    this.query = query;
    this.connectionName = connectionName;
    this.connection = connection;
    this.modelName = modelName;
  }
 
  public Connection getConnection() {
    return connection;
  }

  public void setConnection(Connection connection) {
    this.connection = connection;
  }

  public String getModelName() {
    return modelName;
  }

  public void setModelName(String modelName) {
    this.modelName = modelName;
  }
  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }
  private boolean validate() {
    return !StringUtils.isEmpty(this.modelName) && !StringUtils.isEmpty(this.query) && this.connection != null;  
  }
  public Domain generate() throws SQLModelGeneratorException {
    return generate(this.modelName, this.connectionName, this.connection, this.query);
  }
  
  public Domain generate(String modelName, String connectionName, Connection connection, String query) throws SQLModelGeneratorException {
    
    LocaleType locale = new LocaleType(LocaleHelper.getLocale().toString(), LocaleHelper.getLocale().getDisplayName());
    
    if(validate()) {
    SqlPhysicalModel model = new SqlPhysicalModel();
    String modelID = Settings.getBusinessModelIDPrefix()+ modelName;
    model.setId(modelID);
    model.setName(new LocalizedString(locale.getCode(), modelName));
    model.setDatasource(connectionName);
    SqlPhysicalTable table = new SqlPhysicalTable(model);
    table.setId("INLINE_SQL_1");
    model.getPhysicalTables().add(table);
    table.setTargetTableType(TargetTableType.INLINE_SQL);
    table.setTargetTable(query);
    
    String[] columnHeader = null;
    //String[] columnType = null;
    int[] columnType = null;
      Statement stmt = null;
      ResultSet rs = null;
      try {

        if (!StringUtils.isEmpty(query)) {
          stmt = connection.createStatement();
          stmt.setMaxRows(5);
          rs = stmt.executeQuery(query);
          ResultSetMetaData metadata = rs.getMetaData();
          columnHeader = new String[metadata.getColumnCount()];
          //columnType = new String[metadata.getColumnCount()];
          columnType = new int[metadata.getColumnCount()];
          columnHeader = getColumnNames(metadata);
          columnType = getColumnTypes(metadata);
        } else {
          throw new SQLModelGeneratorException("Query not valid"); //$NON-NLS-1$
        }
      } catch (Exception e) {
        e.printStackTrace();
        throw new SQLModelGeneratorException("Query validation failed", e); //$NON-NLS-1$
      } finally {
        try {
          if (rs != null) {
            rs.close();
          }
          if (stmt != null) {
            stmt.close();
          }
          if (connection != null) {
            connection.close();
          }
        } catch (SQLException e) {
          throw new SQLModelGeneratorException(e);
        }
      }
      
      try {
        Category mainCategory = new Category();
        String categoryID= Settings.getBusinessCategoryIDPrefix()+ modelName;
        mainCategory.setId(categoryID);
        mainCategory.setName(new LocalizedString(locale.getCode(), modelName));
  
        LogicalModel logicalModel = new LogicalModel();
        logicalModel.setId("MODEL_1");
        logicalModel.setName(new LocalizedString(locale.getCode(), modelName));
  
        LogicalTable logicalTable = new LogicalTable();
        logicalTable.setPhysicalTable(table);
        logicalTable.setId("LOGICAL_TABLE_1");
        
        logicalModel.getLogicalTables().add(logicalTable);
        
        for(int i=0;i<columnHeader.length;i++) {
          SqlPhysicalColumn column = new SqlPhysicalColumn(table);
          
          // should get unique id here
          
          column.setId(columnHeader[i]);
          column.setTargetColumn(columnHeader[i]);
          // Get the localized string
          column.setName(new LocalizedString(locale.getCode(), columnHeader[i]));
          // Map the SQL Column Type to Metadata Column Type
          column.setDataType(converDataType(columnType[i]));
          String physicalColumnID = Settings.getPhysicalColumnIDPrefix() + "_" + columnHeader[i];
          column.setId(physicalColumnID);
          table.getPhysicalColumns().add(column);
                  
          LogicalColumn logicalColumn = new LogicalColumn();
          String columnID = Settings.getBusinessColumnIDPrefix();
          logicalColumn.setId(columnID + columnHeader[i]);
          
          // the default name of the logical column.
          // this inherits from the physical column.
          // logicalColumn.setName(new LocalizedString(columnHeader[i]));
          
          logicalColumn.setPhysicalColumn(column);
          logicalColumn.setLogicalTable(logicalTable);
          
          logicalTable.addLogicalColumn(logicalColumn);
          
          mainCategory.addLogicalColumn(logicalColumn);
        }
        
        logicalModel.getCategories().add(mainCategory);
        
        Domain domain = new Domain();
        domain.addPhysicalModel(model);
        
        List<LocaleType> locales = new ArrayList<LocaleType>();
        locales.add(locale);
        domain.setLocales(locales);
        domain.addLogicalModel(logicalModel);
        domain.setId(modelName);
        return domain;

      } catch(Exception e) {
        throw new SQLModelGeneratorException(e);
      }
    } else {
      throw new SQLModelGeneratorException("Input Validation Failed");
    }
  }
 
  private static DataType converDataType(int type)
  {
    switch (type)
    {
    case Types.BIGINT:
    case Types.INTEGER:
    case Types.NUMERIC:
      return DataType.NUMERIC;
    
    case Types.BINARY:
      return DataType.BINARY;

    case Types.BOOLEAN:
      return DataType.BOOLEAN;
    
    case Types.DATE:
    case Types.TIMESTAMP:  
      return DataType.DATE;
    
    case Types.LONGVARCHAR:
    
    case Types.VARCHAR:
      return DataType.STRING;

    default:
      return DataType.UNKNOWN;
    }
  }
  
  /**
   * The following method returns an array of String(java.sql.Types) containing the column types for
   * a given ResultSetMetaData object.
   */
  private String[] getColumnTypesNames(ResultSetMetaData resultSetMetaData) throws SQLException {
    int columnCount = resultSetMetaData.getColumnCount();
    String[] columnTypes = new String[columnCount];

    for(int colIndex=1; colIndex<=columnCount; colIndex++){
      columnTypes[colIndex-1] = resultSetMetaData.getColumnTypeName(colIndex);
    }

    return columnTypes;
  }
  
  /**
   * The following method returns an array of strings containing the column names for
   * a given ResultSetMetaData object.
   */
  public String[] getColumnNames(ResultSetMetaData resultSetMetaData) throws SQLException {
    int columnCount = resultSetMetaData.getColumnCount();
    String columnNames[] = new String[columnCount];

    for(int colIndex=1; colIndex<=columnCount; colIndex++){
      columnNames[colIndex-1] = resultSetMetaData.getColumnName(colIndex);
    }

    return columnNames;
  }
  
  /**
   * The following method returns an array of int(java.sql.Types) containing the column types for
   * a given ResultSetMetaData object.
   */
  public int[] getColumnTypes(ResultSetMetaData resultSetMetaData) throws SQLException {
    int columnCount = resultSetMetaData.getColumnCount();
    int[] columnTypes = new int[columnCount];

    for(int colIndex=1; colIndex<=columnCount; colIndex++){
      columnTypes[colIndex-1] = resultSetMetaData.getColumnType(colIndex);
    }

    return columnTypes;
  }
}
