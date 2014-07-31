/*******************************************************************************
 * Copyright (c) 2013 Lectorius, Inc.
 * Authors:
 * Vijay Pandurangan (vijayp@mitro.co)
 * Evan Jones (ej@mitro.co)
 * Adam Hilss (ahilss@mitro.co)
 *
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *     
 *     You can contact the authors at inbound@mitro.co.
 *******************************************************************************/
package co.mitro.core.server;

public interface ManagerPool {
  /**
   * Aborts any transactions that have been idle for too long.
   * @return number of aborted transactions.
   */
  int abortExpiredTransactions();

  /**
   * Add a new manager to the pool. It must not already be in the pool.
   * @param m Manager to add to the pool.
   */
  // TODO: Rename to addManager()?
  void addManagerToPool(Manager m);

  /**
   * Returns the Manager corresponding to transactionId.
   */
  // TODO: Rename to getManager()?
  Manager getManagerFromUniqueId(String transactionId);

  /**
   * Removes manager from the pool. The manager must be in the pool.
   */
  void removeManager(Manager manager);
}
