package com.lankydanblog.tutorial.services

import com.lankydanblog.tutorial.states.MessageSchema
import com.lankydanblog.tutorial.states.StockState
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.serialization.SingletonSerializeAsToken

@CordaService
class MessageRepository(private val serviceHub: AppServiceHub) :
  SingletonSerializeAsToken() {

  fun findAllNewNotBySender(sender: Party): Vault.Page<StockState> =
    serviceHub.vaultService.queryBy(QueryCriteria.VaultCustomQueryCriteria(builder {
      MessageSchema.MessageEntity::sender.notEqual(sender.name.toString())
    }))

  fun findAllNewBySender(sender: Party): Vault.Page<StockState> =
    serviceHub.vaultService.queryBy(QueryCriteria.VaultCustomQueryCriteria(builder {
      MessageSchema.MessageEntity::sender.equal(sender.name.toString())
    }))

  fun findAllNewBySenderAndType(sender: Party, type: String): Vault.Page<StockState> =
    serviceHub.vaultService.queryBy(QueryCriteria.VaultCustomQueryCriteria(builder {
      MessageSchema.MessageEntity::sender.equal(sender.name.toString())
    }).and(QueryCriteria.VaultCustomQueryCriteria(builder {
      MessageSchema.MessageEntity::type.equal(type)
    })))
}