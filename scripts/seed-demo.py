"""Add a repeatable inventory demo through the normal API; never reset existing data."""
import argparse
from collections import Counter
import json
from pathlib import Path
import time
import requests

PRODUCTS = [
    ('mouse','Wireless Mouse','Peripherals',24.99,15,(120,45,30)),
    ('keyboard','Mechanical Keyboard','Peripherals',59.99,10,(60,25,15)),
    ('hub','USB-C Hub','Electronics',39.99,12,(45,20,10)),
    ('monitor','24-inch Monitor','Electronics',179.99,8,(30,12,6)),
    ('stand','Adjustable Laptop Stand','Peripherals',29.99,10,(80,35,20)),
    ('cable','Ethernet Cable 2m','Electronics',8.99,20,(200,100,60)),
    ('scanner','Barcode Scanner','Electronics',89.99,5,(25,10,3)),
    ('labels','Shipping Labels 100-pack','Packaging',9.99,25,(300,150,80)),
    ('tape','Packing Tape Roll','Packaging',4.99,30,(180,90,45)),
    ('bubble','Bubble Wrap Roll','Packaging',12.99,15,(65,25,10)),
    ('ssd','Portable SSD 1TB','Electronics',119.99,8,(35,14,5)),
    ('gloves','Safety Gloves Pair','Safety',6.49,20,(100,45,15)),
]
DESCRIPTIONS = {
    'mouse': 'Compact wireless optical mouse for everyday workstation use, with a USB receiver, scroll wheel and ambidextrous grip.',
    'keyboard': 'Full-size mechanical keyboard with a number pad and tactile keys for typing, data entry and shared office workstations.',
    'hub': 'USB-C expansion hub for connecting accessories, an external display and removable storage to a laptop through a single port.',
    'monitor': '24-inch desktop monitor for office applications, inventory dashboards and warehouse workstation displays. Supplied as one monitor.',
    'stand': 'Adjustable desktop stand that raises a laptop screen and provides space beneath the device for cables and desk accessories.',
    'cable': 'Two-metre Ethernet cable for connecting computers, barcode stations and network devices to a wired local network.',
    'scanner': 'Handheld barcode scanner for recording product labels during goods receipt, stock counts, order picking and dispatch.',
    'labels': 'Pack of 100 adhesive shipping labels for identifying parcels, printing delivery details and tracking warehouse dispatches.',
    'tape': 'Roll of packing tape for sealing cartons and securing parcels before storage or dispatch. Stock is tracked per roll.',
    'bubble': 'Roll of protective bubble wrap for cushioning electronics and other fragile items during packing, storage and transport.',
    'ssd': 'Portable 1 TB solid-state drive for transferring business files and backing up workstation data. Stock is tracked per drive.',
    'gloves': 'Pair of reusable work gloves for general warehouse handling, including picking, packing and moving cartons.',
}
WAREHOUSES = [('bengaluru','Bengaluru Central','Bengaluru, Karnataka'),
              ('mumbai','Mumbai Distribution','Mumbai, Maharashtra'),
              ('delhi','Delhi Fulfilment','New Delhi, Delhi')]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url',default='http://127.0.0.1:8081')
    args=parser.parse_args()
    base=args.base_url.rstrip('/')+'/api'
    session=requests.Session();session.trust_env=False
    def api(method,path,body=None,**kwargs):
        response=session.request(method,base+path,json=body,timeout=45,**kwargs)
        if not response.ok: raise RuntimeError(f'{method} {path}: HTTP {response.status_code}: {response.text}')
        return response.json() if response.content else None
    products={}; warehouses={}; purchases=[]; sales=[]; transfers=[]
    known=api('GET','/warehouses')
    for code,name,location in WAREHOUSES:
        matches=[w for w in known if w['name']==name and w['location']==location]
        if len(matches)>1: raise RuntimeError('Ambiguous demo warehouse; inspect '+name)
        warehouse=matches[0] if matches else api('POST','/warehouses',{'name':name,'location':location,'status':'ACTIVE'})
        if warehouse['status']!='ACTIVE': raise RuntimeError('Demo warehouse is inactive: '+name)
        warehouses[code]=warehouse
    for code,name,category,price,threshold,stock in PRODUCTS:
        sku='DEMO-'+code.upper()+'-001'
        matches=api('GET','/products',params={'query':sku,'size':100})['content']
        product=next((p for p in matches if p['sku']==sku),None)
        if not product:
            product=api('POST','/products',{'sku':sku,'name':name,'category':category,'price':price,
                        'description':DESCRIPTIONS[code],'reorderLevel':threshold,'active':True})
        elif (product.get('description') or '').strip().lower() in ['', 'presentation demo catalogue']:
            # Upgrade old placeholders while preserving descriptions edited by users.
            payload={k:product[k] for k in ['sku','name','category','price','reorderLevel','active']}
            payload['description']=DESCRIPTIONS[code]
            product=api('PUT','/products/'+product['productId'],payload)
        products[code]=product
    def purchase(label,warehouse,lines,receive=None):
        payload={'supplierId':'demo-supplier-tech' if warehouse!='mumbai' else 'demo-supplier-distribution',
                 'destinationWarehouseId':warehouses[warehouse]['warehouseId'],
                 'items':[{'productId':products[p]['productId'],'quantity':q} for p,q in lines],
                 'idempotencyKey':'demo-v1-po-'+label}
        order=api('POST','/purchase-orders',payload)
        if receive=='all': order=api('POST',f"/purchase-orders/{order['orderId']}/receive",{})
        elif receive:
            order=api('POST',f"/purchase-orders/{order['orderId']}/receive",{'idempotencyKey':'demo-v1-receipt-'+label,
                'items':[{'productId':products[p]['productId'],'quantityReceived':q} for p,q in receive]})
        purchases.append({'demoLabel':label,**order}); return order
    # Stock is received through purchase orders, producing real movement/audit records.
    for index,(code,name,location) in enumerate(WAREHOUSES):
        purchase('opening-'+code,code,[(p[0],p[5][index]) for p in PRODUCTS],receive='all')
    purchase('office-restock','bengaluru',[('mouse',60),('keyboard',40),('hub',20)])
    purchase('delhi-low-stock-restock','delhi',[('monitor',20),('scanner',15)])
    purchase('partial-mumbai-delivery','mumbai',[('ssd',20),('gloves',50)],receive=[('ssd',8),('gloves',20)])
    def sale(label,warehouse,lines,state='RESERVED'):
        order=api('POST','/sales-orders',{'idempotencyKey':'demo-v1-so-'+label,
            'items':[{'productId':products[p]['productId'],'warehouseId':warehouses[warehouse]['warehouseId'],'quantity':q} for p,q in lines]})
        if state in ['COMPLETED','CANCELLED'] and order['status']=='RESERVED':
            action='complete' if state=='COMPLETED' else 'cancel'
            order=api('POST',f"/sales-orders/{order['orderId']}/{action}")
        sales.append({'demoLabel':label,**order})
    sale('office-equipment-ready','bengaluru',[('mouse',10),('keyboard',5)])
    sale('packing-supplies-ready','mumbai',[('tape',20),('labels',30)])
    sale('fulfilled-it-order','bengaluru',[('monitor',4),('hub',6)],'COMPLETED')
    sale('cancelled-gloves-order','delhi',[('gloves',5)],'CANCELLED')
    sale('low-stock-scanners-reserved','delhi',[('scanner',2)])
    def transfer(label,source,destination,lines,state='IN_TRANSIT'):
        record=api('POST','/transfers',{'idempotencyKey':'demo-v1-transfer-'+label,
            'sourceWarehouseId':warehouses[source]['warehouseId'],'destinationWarehouseId':warehouses[destination]['warehouseId'],
            'items':[{'productId':products[p]['productId'],'quantity':q} for p,q in lines]})
        if state in ['COMPLETED','CANCELLED'] and record['status']=='IN_TRANSIT':
            record=api('POST',f"/transfers/{record['transferId']}/{'complete' if state=='COMPLETED' else 'cancel'}")
        transfers.append({'demoLabel':label,**record})
    transfer('mouse-delivery-in-transit','bengaluru','delhi',[('mouse',15)])
    transfer('labels-delivered','bengaluru','mumbai',[('labels',40)],'COMPLETED')
    transfer('cable-move-cancelled','mumbai','delhi',[('cable',25)],'CANCELLED')
    inventory=[]; movements=[]
    for code,product in products.items():
        inventory.extend(api('GET','/inventory/'+product['productId']))
        movements.extend(api('GET','/inventory/'+product['productId']+'/movements'))
    assert len(inventory)==36
    for record in inventory:
        assert 0<=record['reservedQuantity']<=record['quantity']
        assert record['availableQuantity']==record['quantity']-record['reservedQuantity']
    # Re-read states so rerunning after a live demo never reports an old seed status.
    for collection,path,id_key in [(purchases,'purchase-orders','orderId'),(sales,'sales-orders','orderId'),(transfers,'transfers','transferId')]:
        for index,record in enumerate(collection): collection[index]={'demoLabel':record['demoLabel'],**api('GET',f"/{path}/{record[id_key]}")}
    expected={}
    for order in sales:
        if order['status']=='RESERVED':
            for item in order['items']:
                key=(item['productId'],item['warehouseId']); expected[key]=expected.get(key,0)+item['quantity']
    # Other user-created orders may legitimately add reservations to these demo products.
    for record in inventory: assert record['reservedQuantity']>=expected.get((record['productId'],record['warehouseId']),0)
    alerts=api('GET','/alerts/low-stock')
    manifest={'baseUrl':args.base_url,'seedVersion':'demo-v1','products':list(products.values()),'warehouses':list(warehouses.values()),
              'purchaseOrders':purchases,'salesOrders':sales,'transfers':transfers,'inventory':inventory,'alerts':alerts,
              'movementCount':len(movements),'verified':True,
              'note':'Reruns reuse SKUs/warehouses and idempotency keys. Existing records and completed demo actions are not reset.'}
    target=Path(__file__).resolve().parents[1]/'evaluation/demo';target.mkdir(parents=True,exist_ok=True)
    (target/'demo-data.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    lines=['# Demo data','','UI: http://localhost:3000. Refresh once after seeding.','',
           f"{len(products)} products, {len(warehouses)} warehouses, {len(inventory)} stock records, {len(alerts)} active low-stock alerts.",'',
           '## Ready-to-show actions','',
           '- Search **Wireless Mouse** or **USB-C Hub** in global search.',
           '- Open warehouses and compare physical, reserved and available stock.',
           '- Fulfil or cancel **office-equipment-ready** to demonstrate reservations.',
           '- Receive **delhi-low-stock-restock** to resolve monitor/scanner low-stock alerts.',
           '- Receive the remainder of **partial-mumbai-delivery**.',
           '- Complete **mouse-delivery-in-transit** and watch Delhi stock increase.','',
           '| Type | Demo label | ID | Current status |','| --- | --- | --- | --- |']
    for kind,records,id_key in [('Purchase',purchases,'orderId'),('Sales',sales,'orderId'),('Transfer',transfers,'transferId')]:
        lines.extend(f"| {kind} | {r['demoLabel']} | {r[id_key]} | {r['status']} |" for r in records)
    lines+=['','Reproduce without resetting existing data:', '',
            f'`python scripts/seed-demo.py --base-url {args.base_url}`','',
            'The script uses the normal APIs and stable creation/receipt identities. It does not erase stock or reopen completed/cancelled orders. Rerunning verifies current data instead of resetting the demonstration.']
    (target/'README.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print(json.dumps({'products':len(products),'warehouses':len(warehouses),'inventoryRecords':len(inventory),
          'purchaseOrderStates':dict(Counter(o['status'] for o in purchases)),
          'salesOrderStates':dict(Counter(o['status'] for o in sales)),
          'transferStates':dict(Counter(t['status'] for t in transfers)),
          'activeAlerts':len(alerts),'movements':len(movements),'manifest':str(target/'demo-data.json')},indent=2))


if __name__=='__main__': main()
