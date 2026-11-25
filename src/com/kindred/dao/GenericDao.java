package com.kindred.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * GENERIC DAO INTERFACE: the standard CRUD contract for every table.
 *
 *   T  = the model type   (User, Skill, SkillSession ...)
 *   ID = the primary-key type (Integer here)
 *
 * UserDao implements GenericDao<User, Integer>, SkillDao implements
 * GenericDao<Skill, Integer>, and so on. The compiler then guarantees that
 * UserDao.findById() returns Optional<User> and never a Skill (type safety).
 */
// SYLLABUS: Generics - generic interface with two type parameters <T, ID>
// SYLLABUS: CRUD Operations - Create, Read, Update, Delete contract
public interface GenericDao<T, ID> {

    /** CREATE: inserts the entity and returns the generated primary key. */
    ID create(T entity) throws SQLException;

    /** READ one: Optional.empty() when no row has this id. */
    Optional<T> findById(ID id) throws SQLException;

    /** READ all rows. */
    List<T> findAll() throws SQLException;

    /** UPDATE: returns true if a row was changed. */
    boolean update(T entity) throws SQLException;

    /** DELETE: returns true if a row was deleted. */
    boolean delete(ID id) throws SQLException;
}
