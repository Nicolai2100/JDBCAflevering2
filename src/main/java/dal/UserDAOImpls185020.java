package dal;

import dal.dto.IUserDTO;
import dal.dto.UserDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UserDAOImpls185020 implements IUserDAO {
    private Connection createConnection() throws DALException {
        try {
            return DriverManager.getConnection("jdbc:mysql://ec2-52-30-211-3.eu-west-1.compute.amazonaws.com/s185020",
                    "s185020", "iEFSqK2BFP60YWMPlw77I");
        } catch (SQLException e) {
            throw new DALException(e.getMessage());
        }
    }

    @Override
    public void createUser(IUserDTO user) throws DALException {
        Connection conn = null;
        if (user.getUserId() < 1) {
            System.out.println("Error in userID!");
            return;
        }

        try {
            conn = createConnection();
            PreparedStatement pSmtInsertUser = conn.prepareStatement(
                    "INSERT INTO user_table " +
                            "VALUES(?,?,?)");
            pSmtInsertUser.setInt(1, user.getUserId());
            pSmtInsertUser.setString(2, user.getUserName());
            pSmtInsertUser.setString(3, user.getIni());

            // Bør gøres atomic - da bruger ellers oprettes uden roller!
            pSmtInsertUser.executeUpdate();
            //Først oprettes brugeren - så indsættes rollerne.
            setUserRoles(conn, user);
            System.out.println("The user was successfully created in the database system");

        } catch (SQLException e) {
            System.out.println("Error! " + e.getMessage());
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    @Override
    public IUserDTO getUser(int userId) throws DALException {
        boolean empty = true;
        UserDTO returnUser = new UserDTO();
        Connection conn = null;
        try {
            conn = createConnection();
            PreparedStatement pSmtSelectUser = conn.prepareStatement(
                    "SELECT * " +
                            "FROM user_table " +
                            "WHERE userid = ?");

            pSmtSelectUser.setInt(1, userId);
            ResultSet rs = pSmtSelectUser.executeQuery();
            while (rs.next()) {
                empty = false;
                returnUser.setUserId(rs.getInt(1));
                returnUser.setUserName(rs.getString(2));
                returnUser.setIni(rs.getString(3));
                returnUser.setRoles(getUserRoleList(conn, userId));
            }
            if (empty) {
                System.out.println("No such user in the database!");
                return null;
            }
        } catch (SQLException e) {
            System.out.println("Error! " + e.getMessage());
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
        return returnUser;
    }

    @Override
    public List<IUserDTO> getUserList() throws DALException {
        List<IUserDTO> userList = new ArrayList<>();
        Connection conn = null;
        try {
            conn = createConnection();
            PreparedStatement pSmtSelectAllTable = conn.prepareStatement(
                    "SELECT * " +
                            "FROM user_table");
            UserDTO returnUser;
            ResultSet rs = pSmtSelectAllTable.executeQuery();

            while (rs.next()) {
                returnUser = new UserDTO();
                int userIDReturn = rs.getInt("userID");
                returnUser.setUserId(userIDReturn);
                returnUser.setUserName(rs.getString("username"));
                returnUser.setIni(rs.getString("ini"));
                userList.add(returnUser);
            }

            pSmtSelectAllTable.close();
        } catch (SQLException e) {
            System.out.println("Error " + e.getMessage());
        } finally {
            getAllUsersRoles(conn, userList);
            try {
                conn.close();
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
        return userList;
    }

    @Override
    public void updateUser(IUserDTO user) throws DALException {
        Connection conn = null;
        try {
            conn = createConnection();

            if (!peekUser(conn, user.getUserId())) {
                System.out.println("No such user in the database!");
            } else {
                PreparedStatement pSmtUpdateUser = conn.prepareStatement(
                        "UPDATE user_table " +
                                "SET " +
                                "username = ?, " +
                                "ini = ? " +
                                "WHERE userid = ? ");
                pSmtUpdateUser.setString(1, user.getUserName());
                pSmtUpdateUser.setString(2, user.getIni());
                pSmtUpdateUser.setInt(3, user.getUserId());
                pSmtUpdateUser.executeUpdate();
                //Hvis brugeren har fået nye roller oprettes de - men sletter ikke, hvis han har fået dem fjernet!

                roleTransAct(conn, user);
                System.out.println("The user was successfully updated!");
                pSmtUpdateUser.close();
            }
        } catch (SQLException e) {
            System.out.println("Error! " + e.getMessage());
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    @Override
    public void deleteUser(int userId) throws DALException {
        int result;
        Connection conn = null;
        try {
            conn = createConnection();
            PreparedStatement pSmtDeleteUser = conn.prepareStatement(
                    "DELETE FROM user_table " +
                            "WHERE userid = ?");
            pSmtDeleteUser.setInt(1, userId);
            result = pSmtDeleteUser.executeUpdate();
            if (result == 1) {
                System.out.println("The user with user-ID: " + userId + " was successfully deleted from the system.");
            } else {
                System.out.println("Error no such user exists in the database!");
            }
            pSmtDeleteUser.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    /**
     * Metoden henter en specifik brugers roller og returnerer dem i en liste.
     */
    public List<String> getUserRoleList(Connection conn, int userID) {
        List<String> userRoleList = new ArrayList<>();
        try {
            PreparedStatement pSmtSelectUserRoles = conn.prepareStatement(
                    "SELECT role_table.role " +
                            "FROM (user_roles " +
                            "JOIN role_table ON user_roles.roleid = role_table.roleid)" +
                            "WHERE userid = ?");

            pSmtSelectUserRoles.setInt(1, userID);
            ResultSet rs = pSmtSelectUserRoles.executeQuery();
            while (rs.next()) {
                userRoleList.add(rs.getString("role"));
            }
            Collections.reverse(userRoleList);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return userRoleList;
    }

    /**
     * Metoden henter og gemmer roller for alle brugere i en liste.
     */
    public void getAllUsersRoles(Connection conn, List<IUserDTO> userDTOList) {

        for (IUserDTO user : userDTOList) {
            List<String> userRoleList = getUserRoleList(conn, user.getUserId());
            user.setRoles(userRoleList);
        }
    }

    /**
     * Metoden opretter og gemmer roller for en brugere i user_roles.
     */
    public void setUserRoles(Connection conn, IUserDTO user) throws DALException {
        try {
            PreparedStatement pSmtInsertUserRole = conn.prepareStatement(
                    "INSERT INTO user_roles " +
                            "VALUES(?,?)");

            pSmtInsertUserRole.setInt(1, user.getUserId());
            for (int i = 0; i < user.getRoles().size(); i++) {
                String role = user.getRoles().get(i);
                int roleInt = getRoleID(conn, role);
                pSmtInsertUserRole.setInt(2, roleInt);
                pSmtInsertUserRole.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("Error in user_roles " + e.getMessage());
        }
    }

    /**
     * Metoden henter en specifik rolles roleid fra role_table og returnerer den.
     */
    public int getRoleID(Connection conn, String role) throws DALException {
        int returnInt = 0;
        try {
            PreparedStatement pStmtSelectRoleId = conn.prepareStatement(
                    "SELECT  roleid  " +
                            "FROM role_table " +
                            "WHERE role = ?");
            pStmtSelectRoleId.setString(1, role);

            ResultSet rs = pStmtSelectRoleId.executeQuery();
            if (rs.next()) {
                returnInt = rs.getInt("roleid");
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        if (returnInt == 0) {
            // Hvis den returnerer 0 betyder det at rollen ikke findes, og derfor skal den oprettes.
            returnInt = createNewRole(conn, role);
        }
        return returnInt;
    }

    /**
     * Metoden opretter nye roller i role_table og returnerer rollens roleid.
     */
    public int createNewRole(Connection conn, String userRole) {
        int newRoleID = 0;
        try {
            PreparedStatement pSmtInsertUserRole = conn.prepareStatement(
                    "INSERT INTO role_table " +
                            "VALUES(?,?)");
            newRoleID = getMaxFromRoleTable(conn);
            pSmtInsertUserRole.setInt(1, newRoleID);
            pSmtInsertUserRole.setString(2, userRole);
            pSmtInsertUserRole.execute();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return newRoleID;
    }

    /**
     * Metoden henter den maksimale roleid fra role_table og returnere denne inkrementeret med 1.
     * Dette bruges, når der skal oprettes nye roller i role_table
     */
    public int getMaxFromRoleTable(Connection conn) {
        int resultInt = 0;
        try {
            PreparedStatement prep = conn.prepareStatement(
                    "SELECT MAX(roleid) AS maxid " +
                            "FROM role_table ");
            ResultSet rs = prep.executeQuery();
            if (rs.next())
                resultInt = rs.getInt("maxid");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return resultInt + 1;
    }

    /**
     * Metoden bruges til at undersøge om der eksisterer en bruger med et
     * specifikt userid i user_table. Den returnerer false, hvis der ikke gør.
     */
    public boolean peekUser(Connection conn, int userID) throws DALException {
        int returnInt = 0;
        try {
            PreparedStatement prep = conn.prepareStatement(
                    "SELECT COUNT(*) AS uservalidity " +
                            "FROM user_table " +
                            "WHERE userid = ?");
            prep.setInt(1, userID);

            ResultSet rs = prep.executeQuery();
            while (rs.next()) {
                returnInt = rs.getInt("uservalidity");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (returnInt == 0) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Metoden sletter alle roller for en specifik bruger.
     * Dette bruges når brugeren bliver opdateret, for at undgå at
     * brugeren får flere roller end denne bør have.
     */
    public void deleteAllRoles(Connection conn, int userid) {
        try {
            PreparedStatement prep = conn.prepareStatement(
                    "DELETE FROM user_roles " +
                            "WHERE userid = ?");
            prep.setInt(1, userid);
            prep.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
    public void roleTransAct(Connection conn, IUserDTO user) {
        List<String> newUserRoles = user.getRoles();
        List<String> DBUserRoles = getUserRoleList(conn, user.getUserId());
        try {
            PreparedStatement prepDelete = conn.prepareStatement(
                    "DELETE FROM user_roles " +
                            "WHERE userid = ? " +
                            "AND roleid = ?"
            );
            prepDelete.setInt(1, user.getUserId());

            PreparedStatement prepInsert = conn.prepareStatement(
                    "INSERT INTO user_roles " +
                            "VALUES(?,?)"
            );
            prepInsert.setInt(1, user.getUserId());
            //Transaction begynder
            conn.setAutoCommit(false);
            //De gamle roller der ikke indgår i det opdaterede rollesæt skal slettes fra DB.
            for (String role : DBUserRoles) {
                if (!newUserRoles.contains(role)) {
                    int roleid = getRoleID(conn, role);
                    prepDelete.setInt(2, roleid);
                    int success = prepDelete.executeUpdate();
                    if (success < 1) {
                        conn.rollback();
                    }
                }
            }
            //De nye roller der ikke indgår i det rollesæt der ligger i DB skal indsættes.
            for (String role : newUserRoles) {
                if (!DBUserRoles.contains(role)) {
                    int roleid = getRoleID(conn, role);
                    prepInsert.setInt(2, roleid);
                    int success = prepInsert.executeUpdate();
                    if (success < 1) {
                        conn.rollback();
                    }
                }
            }
            conn.commit();
        } catch (SQLException | DALException e) {
            System.out.println(e.getMessage());
        }
    }
}
