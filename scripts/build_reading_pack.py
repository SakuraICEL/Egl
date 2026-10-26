import json
from pathlib import Path

def q(prompt,options,answer,explanation):return dict(prompt=prompt,options=options,answer=answer,explanation=explanation)
passages=[dict(id='moli_libraries_v1',title='Libraries beyond the Bookshelf',source='莫璃原创练习 · 非历年真题',
text='''When a public library in a coastal town announced that it would lend tools as well as books, some residents assumed that the building was abandoning its purpose. A library, they argued, should concentrate on reading. Yet its director saw the proposal from a different perspective: both books and tools enable people to do something they could not otherwise afford to do.

The programme began with thirty objects, including sewing machines and equipment for measuring energy use. Staff did not choose the most fashionable products. Instead, they examined requests from residents and selected things that were useful but rarely needed. A family might need a drill for one afternoon, for example, without needing to own one permanently.

The first six months revealed unexpected difficulties. Tools required inspection after every return, and some borrowers needed guidance before using them safely. The programme therefore introduced short demonstrations and limited certain items to adults. These measures increased the workload, but they also reduced damage and encouraged conversations between neighbours.

An evaluation found that borrowing had saved residents money, although the director warned against treating savings as the only measure of success. Several borrowers said that they had learned a skill from someone they met at the library. Others felt more comfortable asking for help there than in a commercial shop. The project suggests that public institutions can adapt to changing needs without losing their original commitment to shared access.''',
vocabulary=['assume','perspective','proposal','concentrate','equipment','inspection','evaluation','commitment'],questions=[
q('What principle connects lending books with lending tools?',['Both should generate a profit.','Both expand access to useful resources.','Both require users to buy equipment.','Both replace professional training.'],1,'首段末句指出，书和工具都能让人做原本负担不起的事，核心是资源可及性。'),
q('How did staff select the first objects?',['They bought the newest products.','They copied commercial shops.','They considered residents’ needs and infrequent use.','They accepted only donated machines.'],2,'第二段明确说明，工作人员查看居民需求，选择实用但不常使用的物品。'),
q('Why were demonstrations introduced?',['To support safer use and reduce damage.','To advertise new books.','To eliminate all staff work.','To encourage permanent purchases.'],0,'第三段将示范与安全指导联系起来，并说明这些措施减少了损坏。'),
q('What does the director caution against?',['Allowing adults to borrow tools.','Changing the programme after evaluation.','Measuring the programme only by financial savings.','Talking to neighbours at the library.'],2,'第四段说不能把节省的钱当作唯一成功标准，还应考虑学习和社区联系。'),
q('Which title best expresses the passage’s argument?',['Public Libraries Must Stop Lending Books','Commercial Shops Offer Better Guidance','Fashion Determines Public Services','Shared Access Can Take New Forms'],3,'全文以工具借阅为例，说明公共机构能够拓展共享资源的形式。')]),
dict(id='moli_cities_v1',title='A Cooler Route to School',source='莫璃原创练习 · 非历年真题',
text='''In many cities, a route that appears shortest on a map is not necessarily the best route on a hot afternoon. A group of researchers investigated this problem by placing temperature sensors along streets used by children walking to school. Their measurements showed that two streets only a short distance apart could expose pedestrians to very different conditions.

One reason was shade. Mature trees protected pavements from direct sunlight, while wide roads and large areas of dark paving absorbed heat. However, the researchers also found that a simple count of trees could be misleading. A young tree offered little protection, and a tree on the wrong side of a street might provide no shade at the time pupils travelled home.

The team worked with local families to design an alternative walking map. Rather than recommending a single route to everyone, it indicated where shade was available at different hours. Families could then balance journey length against comfort. The map was useful immediately, but the researchers stressed that information alone would not solve the underlying problem.

Long-term improvement required investment in street design. The city began by improving crossings and planting trees along routes serving schools with the fewest existing resources. Residents were invited to help maintain new trees, but responsibility for watering remained with the city. This distinction mattered: a public project should not depend entirely on unpaid work by people who already had limited time.

The study illustrates why climate adaptation involves more than installing technology. Sensors can reveal a problem, yet effective responses also require attention to daily routines, unequal access and the maintenance of public infrastructure.''',
vocabulary=['route','investigate','expose','pedestrian','misleading','alternative','investment','infrastructure'],questions=[
q('What did the temperature measurements show?',['All nearby streets had identical conditions.','School buildings were the only source of heat.','Nearby streets could differ substantially in heat exposure.','Children always chose shaded routes.'],2,'首段末句指出，相距很近的街道也可能有很不同的行人暴露条件。'),
q('Why can counting trees be misleading?',['All trees increase pavement temperature.','Shade depends on tree size, location and time.','Trees provide protection only at night.','Tree numbers cannot be measured.'],1,'第二段以幼树和位置不合适的树为例，说明树的数量不足以代表实际遮阴。'),
q('What was a limitation of the walking map?',['It did not by itself fix street conditions.','It forced everyone to use one route.','It could be used only after new trees matured.','It measured only commercial roads.'],0,'第三段末句说，仅提供信息不能解决底层问题。'),
q('Why did the city retain responsibility for watering?',['Residents were forbidden to help.','Sensors could water the trees automatically.','Watering had no influence on survival.','The project should not rely entirely on unpaid resident labour.'],3,'第四段强调公共工程不能完全依赖时间有限居民的无偿劳动。'),
q('What is the main conclusion?',['Technology alone guarantees effective adaptation.','Shorter journeys are always safer.','Climate responses require social and maintenance considerations as well as data.','Street improvements should prioritise the wealthiest schools.'],2,'结尾总结，除数据和技术外还要关注生活习惯、资源不平等及基础设施维护。')]),
dict(id='moli_learning_v1',title='The Productive Pause',source='莫璃原创练习 · 非历年真题',
text='''A student preparing for an examination may feel productive while reading the same page repeatedly. Familiar sentences are processed quickly, and that speed can be mistaken for understanding. The difficulty often becomes apparent only when the book is closed and the student tries to explain the material without looking at it.

One alternative is retrieval practice: attempting to recall information before checking an answer. This attempt need not succeed every time to be useful. An incorrect response can reveal a gap that would remain hidden during passive reading, provided that the learner receives accurate feedback afterwards. Repeatedly rehearsing an error without correction, in contrast, offers little benefit.

Timing also matters. If the same item is tested immediately after its answer has been shown, success may reflect short-term accessibility rather than lasting knowledge. Spreading practice over time makes recall more demanding. For that reason, a learner may perform worse during a practice session while developing a more durable memory.

Digital tools can help organise this process, but a schedule is only an estimate. Illness, stress and changes in context can affect performance. A useful application should therefore allow learners to report uncertainty honestly instead of rewarding them merely for selecting a positive response. It should also distinguish the number of practice attempts from the number of different items studied.

None of this means that reading has no value. Reading introduces context and connects ideas, while retrieval checks whether those connections can be reconstructed. The most practical approach combines the two: read with a purpose, pause to recall, check the result and revisit difficult material later.''',
vocabulary=['retrieve','accurate','passive','durable','uncertainty','distinguish','context','reconstruct'],questions=[
q('What can be mistaken for understanding?',['The effort of answering a new question.','The speed of processing familiar sentences.','The ability to explain without a book.','The correction of a wrong answer.'],1,'首段指出熟悉句子被快速加工时，人可能把这种流畅感误当成理解。'),
q('When can an incorrect retrieval attempt be useful?',['When it is followed by accurate feedback.','When the error is repeated without correction.','When the learner never checks an answer.','When the student avoids difficult material.'],0,'第二段明确以事后准确反馈作为错误回忆仍能有用的条件。'),
q('Why may spaced practice feel more difficult?',['It removes all context from learning.','It permanently lowers knowledge.','It makes recall more demanding after a delay.','It prevents students from reading.'],2,'第三段说明间隔练习增加回忆难度，练习表现变差不一定意味着长期记忆更差。'),
q('What should a useful learning app encourage?',['Positive ratings regardless of confidence.','Confusing attempts with unique items.','Rigid schedules that ignore circumstances.','Honest reports of uncertainty.'],3,'第四段强调应用应允许学习者诚实表达不确定性。'),
q('Which strategy does the author recommend?',['Only rereading the same page.','Combining purposeful reading, retrieval, feedback and later review.','Using only a fixed digital schedule.','Avoiding any task that produces mistakes.'],1,'末段提出把阅读、暂停回忆、核对结果和日后复习结合起来。')])]
Path('app/src/main/assets/reading-pack.json').write_text(json.dumps(dict(version=1,passages=passages),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Created',len(passages),'original passages;',sum(len(p['questions']) for p in passages),'questions')
