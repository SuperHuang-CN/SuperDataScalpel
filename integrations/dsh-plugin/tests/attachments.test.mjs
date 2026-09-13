import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { ChatAttachments } from '../dist/attachments.js';
import { Sessions } from '../dist/sessions.js';
import { attachmentMessageInput, messageInput } from '../dist/contracts.js';

const table = () => { const rows = new Map(); rows.put = async (id, row) => { rows.set(id, row); }; return rows; };
function fixture() {
  const id = randomUUID(), other = randomUUID(), tools = new Map(), bytes = new Map();
  const storage = { attachments: table(), messages: table(), sessions: table(), own(key) {
    const result = this.sessions.get(key); if (!result) throw Object.assign(new Error('session'), {status:404}); return result;
  } };
  storage.sessions.set(id, {sessionId:id,provider:'p',model:'m',state:'READY'});
  storage.sessions.set(other, {sessionId:other,provider:'p',model:'m',state:'READY'});
  const ctx = { attachments: {
    imageLimits: {maxImagesPerMessage:5,maxMessageImageBytes:26214400},
    async saveFile({data,name}) { const attachmentId=createHash('sha256').update(data).digest('hex'); bytes.set(attachmentId, data); return {attachmentId,bytes:data.length,name}; },
    async saveImage({data,name,mediaType}) { const file=await this.saveFile({data,name}); return {...file,mediaType,width:10,height:10}; },
    async *readFileStream(ref) { yield bytes.get(ref.attachmentId); }
  }, llm:{resolveModelInfo:async()=>({inputModalities:['text','image']})},tools:{register:tool=>tools.set(tool.name,tool)} };
  return {id,other,storage,ctx,tools,attachments:new ChatAttachments(ctx,storage)};
}
const upload = (name='data.csv', text='city,value\n杭州,123') => ({clientAttachmentId:randomUUID(),name,mediaType:'text/csv',data:Buffer.from(text).toString('base64'),extractedText:text});
test('attachment receipts are idempotent and reject altered bytes, foreign sessions, and archive writes',async()=>{
  const f=fixture(), input=upload(); const result=await f.attachments.upload(f.id,input);
  assert.equal((await f.attachments.upload(f.id,input)).id,result.id);
  await assert.rejects(f.attachments.upload(f.id,{...input,name:'other.csv'}),e=>e.code==='BRIDGE_ATTACHMENT_CONFLICT');
  await assert.rejects(f.attachments.download(f.other,result.id),e=>e.status===404);
  assert.throws(()=>fixture().attachments.own(f.id,result.id),e=>e.status===404);
  f.storage.sessions.get(f.id).archived=true;
  await assert.rejects(f.attachments.upload(f.id,upload()),e=>e.code==='BRIDGE_SESSION_ARCHIVED');
  assert.equal((await f.attachments.download(f.id,result.id)).data,input.data);
});
test('native image/file blocks and model image gating are preserved; raw references are never accepted',async()=>{
  const f=fixture(); const file=await f.attachments.upload(f.id,upload());
  const image=await f.attachments.upload(f.id,{...upload('a.png'),mediaType:'image/png'});
  const content=await f.attachments.content(f.id,[file.id,image.id]);
  assert.deepEqual(content.map(b=>b.type),['file','text','image']);
  f.ctx.attachments.imageLimits.maxImagesPerMessage=0;
  await assert.rejects(f.attachments.content(f.id,[image.id]),e=>e.status===413);
  f.ctx.attachments.imageLimits.maxImagesPerMessage=5;
  f.ctx.llm.resolveModelInfo=async()=>({inputModalities:['text']});
  await assert.rejects(f.attachments.content(f.id,[image.id]),e=>e.code==='BRIDGE_MODEL_IMAGE_UNSUPPORTED');
  assert.ok(attachmentMessageInput.safeParse({clientMessageId:randomUUID(),attachmentIds:[file.id]}).success);
  for (const body of [{clientMessageId:randomUUID(),attachmentIds:[file.id,file.id]}, {clientMessageId:randomUUID(),text:'  '},
    {clientMessageId:randomUUID(),text:'x',images:[{attachmentId:'foreign'}]}]) assert.equal(attachmentMessageInput.safeParse(body).success,false);
  assert.equal(messageInput.safeParse({clientMessageId:randomUUID(),attachmentIds:[file.id]}).success,false);
});
test('attachment tool reads sent files only, paginates without loss and never exposes another session',async()=>{
  const f=fixture(), text='杭州'.repeat(16000), item=await f.attachments.upload(f.id,upload('long.txt',text));
  f.attachments.mount(f.ctx,f.id); const tool=f.tools.get('attachment_read');
  await assert.rejects(tool.execute({attachmentId:item.id},{}),e=>e.status===404);
  f.storage.messages.set('message',{sessionId:f.id,state:'ACCEPTED',attachmentIds:[item.id]});
  let value='',offset=0,more=true;
  while(more){const page=await tool.execute({attachmentId:item.id,offset},{});value+=page.text;offset=page.nextOffset;more=page.hasMore;}
  assert.equal(value,text);
  const other=await f.attachments.upload(f.other,upload());
  await assert.rejects(tool.execute({attachmentId:other.id},{}),e=>e.status===404);
});
test('history restores original user text and authorized attachment cards without generated handles',async()=>{
  const f=fixture(), item=await f.attachments.upload(f.id,upload()), messageId=randomUUID();
  f.storage.messages.set(`${f.id}:${messageId}`,{sessionId:f.id,messageId,state:'ACCEPTED',attachmentIds:[item.id],userText:'分析这份表格'});
  const events=[{seq:0,type:'user/message',surfaceOp:'append',data:{id:messageId,role:'user',source:{kind:'user'},content:[{type:'text',text:'internal file handle'}]}}];
  const ctx={...f.ctx,sessionPersistence:{open:async()=>({read:async()=>({events}),close:async()=>{}})}};
  const sessions=new Sessions(ctx,{presetId:'datascalpel-admin'},f.storage,{},()=>{});
  const page=await sessions.recentMessages(f.id,undefined,50);
  assert.deepEqual(page.items[0].content,[{type:'text',text:'分析这份表格'}]);
  assert.equal(page.items[0].attachments[0].id,item.id);
});
