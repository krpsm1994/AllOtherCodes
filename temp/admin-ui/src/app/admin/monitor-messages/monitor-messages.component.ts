import { Component, OnInit } from '@angular/core';
import { CommonModule, SlicePipe } from '@angular/common';
import { NgxPaginationModule } from 'ngx-pagination';
import { RestCallService } from 'src/app/services/rest-call.service';
import { ToastrService } from 'ngx-toastr';
import { LoggingService } from 'src/app/services/logging.service';
import { Subscription } from 'rxjs';
import { HttpResponse, HttpParams } from '@angular/common/http';

@Component({
  selector: 'app-monitor-messages',
  standalone: true,
  imports: [CommonModule, NgxPaginationModule, SlicePipe],
  templateUrl: './monitor-messages.component.html',
  styleUrls: ['./monitor-messages.component.less']
})
export class MonitorMessagesComponent implements OnInit {

  private monitorMessageUri = '/../monitor';

  monitorMessages: string;
  messages: Array<string>;
  connections = new Array<Array<string>>();
  connectionMessages = new Map<string, Array<string>>();
  checkedmessages: Map<string, string>;

  page = new Map<string, number>();

  private getRestServiceSubscription: Subscription;
  private params: HttpParams;

  constructor(private restService: RestCallService,
    private toastr: ToastrService,
    private log: LoggingService) {
    this.params = new HttpParams().set('displayMessages', '');
  }

  ngOnInit() {
    this.getRestServiceSubscription = this.restService.get<any>('', this.monitorMessageUri, this.params)
      .subscribe(
        (response: HttpResponse<any>) => {
          if (response.body) {
            this.monitorMessages = response.body;
            this.log.debug('Message(s) retrieved');
            this.toastr.info('Message(s) retrieved');
            // get messages for each connection.
            this.messages = this.monitorMessages.substring(0, this.monitorMessages.indexOf('Messages displayed from JMS_DAMLINK_ERROR_QUEUE'))
              .replace(/&emsp;/g, '').replace(/\t/g, '').split('<br/>').filter(Boolean).map(x => x.trim());
            // get number of messages for each connection, which are mentioned at the end.
            this.connections = this.monitorMessages.substring(this.monitorMessages.indexOf('For connectionId')).replace(/&emsp;/g, '')
              .replace(/\t/g, '').split('<br/>').filter(Boolean).map(x => x.trim())
              .map(z => z.match(/For connectionId.*\d/g))
              .filter(Boolean).map(z => z[0].substring(18).split(', '));
            this.checkedmessages = new Map();
            this.connections.forEach(connection => {
              this.page.set(connection[0], 1);
            });
            this.messages.forEach(message => {
              const regex = /(?:OTMM connectionId=)([^;]*)/g;
              const match = regex.exec(message);
              if (match) {
                const data = this.connectionMessages.get(match[1]);
                data ? data.push(message)
                  : this.connectionMessages.set(match[1], new Array<string>(message));
              }
            });
          } else {
            this.log.error('Somehow body is empty');
            this.toastr.error('Somehow body is empty');
          }
        }
      );
  }

  /**
   * Update checked messages map with currently checked messages for deletion/recovery
   * @param connectionId name of connection
   * @param event event fired
   */
  updateCheckOption(connectionId: string, event: Event) {
    const messageId = (event.target as Element).id;
    const checked = (event.target as HTMLInputElement).checked;
    this.checkedmessages.clear();
    if (checked) {
      this.checkedmessages.set(connectionId, messageId);
    }
  }

  /**
   * Delete single message or whole connection messages
   * @param connectionId name of connection
   */
  onAction(connectionId: any, event: Event) {
    const action = (event.target as HTMLInputElement).id;
    console.log(action);
    const messages = this.connectionMessages.get(connectionId);
    for (const message of messages) {
      // to fetch message id from the stored messages.
      const regex = /(?:JMS messageId=)([^;]*)/g;
      const messageId = regex.exec(message);
      const checked = this.checkedmessages.get(connectionId);
      // if message id exist in checked list, then delete/recover that message
      if (messageId && checked && checked === messageId[1]) {
        this.params = action === 'deletemessage' ? new HttpParams().set('deleteMessage', '').set('messageId', messageId[1])
          : new HttpParams().set('recoverMessage', '').set('messageId', messageId[1])
          ;

        this.getRestServiceSubscription = this.restService.get<any>('', this.monitorMessageUri, this.params)
          .subscribe(
            (response: HttpResponse<any>) => {
              if (response.body) {
                this.monitorMessages = response.body;
                if (this.monitorMessages.includes('Messages deleted from JMS_DAMLINK_ERROR_QUEUE: 1') ||
                  this.monitorMessages.includes('Messages recovered from JMS_DAMLINK_ERROR_QUEUE: 1')) {
                  // delete number of messages in that connection
                  this.connections.forEach(connection => {
                    if (connection[0] === connectionId) {
                      connection[1] = '' + (+connection[1] - 1);
                    }
                    // if number of message is 0, then delete connection entry
                    if (connection[0] === connectionId && connection[1] < '1') {
                      const index = this.connections.indexOf(connection);
                      this.connections.splice(index, 1);
                    }
                  }
                  );
                  const tempMessage = this.connectionMessages.get(connectionId);
                  this.connectionMessages.set(connectionId, tempMessage.filter(x => x !== message));
                  this.checkedmessages.delete(connectionId);
                }
              }
            }
          );
        break;
      } else if (messageId && checked === undefined) { /* else delete whole all message of that connection */
        this.params = action === 'deletemessage' ? new HttpParams().set('deleteMessages', '').set('connectionId', connectionId)
          : new HttpParams().set('recoverMessages', '').set('connectionId', connectionId);
        console.log('delete all messags of this connecitons');
        this.getRestServiceSubscription = this.restService.get<any>('', this.monitorMessageUri, this.params)
          .subscribe(
            (response: HttpResponse<any>) => {
              if (response.body) {
                this.monitorMessages = response.body;
                if (this.monitorMessages.includes('Messages recovered from JMS_DAMLINK_ERROR_QUEUE') ||
                  this.monitorMessages.includes('Messages deleted from JMS_DAMLINK_ERROR_QUEUE')) {
                  // delete number of messages in that connection
                  this.connectionMessages.delete(connectionId);
                  this.connections = this.connections.filter(x => x[0] !== connectionId);
                }
              }
            }
          );
        break;
      }
    }
  }



}
